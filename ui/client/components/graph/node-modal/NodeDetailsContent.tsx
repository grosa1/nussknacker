/* eslint-disable i18next/no-literal-string */
import {cloneDeep, get, has, isEqual, partition, set, sortBy, startsWith} from "lodash"
import React, {PropsWithChildren, SetStateAction, useCallback, useEffect, useMemo, useRef, useState} from "react"
import {v4 as uuid4} from "uuid"
import {DEFAULT_EXPRESSION_ID} from "../../../common/graph/constants"
import ProcessUtils from "../../../common/ProcessUtils"
import {StateForSelectTestResults} from "../../../common/TestResultUtils"
import {InputWithFocus} from "../../withFocus"
import NodeUtils from "../NodeUtils"
import MapVariable from "./../node-modal/MapVariable"
import AdditionalProperty, {AdditionalPropertyConfig} from "./AdditionalProperty"
import BranchParameters from "./BranchParameters"
import ExpressionField from "./editors/expression/ExpressionField"
import Field, {FieldType} from "./editors/field/Field"
import {allValid, Error, errorValidator, mandatoryValueValidator, Validator} from "./editors/Validators"
import ParameterList from "./ParameterList"
import {adjustParameters} from "./ParametersUtils"
import SubprocessInputDefinition from "./subprocess-input-definition/SubprocessInputDefinition"
import SubprocessOutputDefinition from "./SubprocessOutputDefinition"
import Variable from "./Variable"
import {refParameters, serviceParameters} from "./NodeDetailsContent/helpers"
import {EdgesDndComponent, WithTempId} from "./EdgesDndComponent"
import {NodeDetails} from "./NodeDetailsContent/NodeDetails"
import {
  Edge,
  EdgeKind,
  NodeType,
  NodeValidationError,
  Parameter,
  ProcessDefinitionData,
  ProcessId,
  UIParameter,
  VariableTypes,
} from "../../../types"
import {UserSettings} from "../../../reducers/userSettings"
import {ValidationRequest} from "../../../actions/nk"
import {FieldLabel, findParamDefinitionByName} from "./FieldLabel"
import {TestResultsWrapper} from "./TestResultsWrapper"
import NodeErrors from "./NodeErrors"
import NodeAdditionalInfoBox from "./NodeAdditionalInfoBox"

export interface NodeDetailsContentProps {
  isEditMode?: boolean,
  dynamicParameterDefinitions?: UIParameter[],
  currentErrors?: NodeValidationError[],
  processId?: ProcessId,
  additionalPropertiesConfig?: Record<string, AdditionalPropertyConfig>,
  showValidation?: boolean,
  showSwitch?: boolean,
  findAvailableVariables?: ReturnType<typeof ProcessUtils.findAvailableVariables>,
  processDefinitionData?: ProcessDefinitionData,
  node: NodeType,
  edges?: Edge[],
  expressionType?,
  originalNodeId?: NodeType["id"],
  nodeTypingInfo?,
  updateNodeData?: (processId: string, validationRequestData: ValidationRequest) => void,
  findAvailableBranchVariables?,
  processProperties?,
  pathsToMark?: string[],
  onChange?: (node: NodeType, outputEdges?: Edge[]) => void,
  variableTypes?: VariableTypes,
  userSettings: UserSettings,
}

interface State {
  editedNode: NodeType,
  edges: WithTempId<Edge>[],
  unusedParameters: Parameter[],
}

export interface NodeDetailsContentProps2 extends NodeDetailsContentProps {
  parameterDefinitions: UIParameter[],
  originalNode: NodeType,
  editedNode: NodeType,
  setEditedNode: DispatchWithCallback<SetStateAction<NodeType>>,
}

function getParameterDefinitions(processDefinitionData: ProcessDefinitionData, node: NodeType, dynamicParameterDefinitions?: UIParameter[]): UIParameter[] {
  return dynamicParameterDefinitions || ProcessUtils.findNodeObjectTypeDefinition(node, processDefinitionData.processDefinition)?.parameters
}

function hasOutputVar(node: NodeType, processDefinitionData: ProcessDefinitionData): boolean {
  const returnType = ProcessUtils.findNodeObjectTypeDefinition(node, processDefinitionData.processDefinition)?.returnType
  return !!returnType || !!node.outputVar
}

function IdField({
  isMarked,
  isEditMode,
  showValidation,
  editedNode,
  onChange,
  children,
}: PropsWithChildren<{ isMarked: boolean, isEditMode: boolean, showValidation: boolean, editedNode: NodeType, onChange: (newValue) => void }>): JSX.Element {
  const validators = [mandatoryValueValidator]
  return (
    <Field
      type={FieldType.input}
      isMarked={isMarked}
      readOnly={!isEditMode}
      showValidation={showValidation}
      className={!showValidation || allValid(validators, [editedNode.id]) ? "node-input" : "node-input node-input-with-error"}
      validators={validators}
      value={editedNode.id}
      onChange={onChange}
      autoFocus
    >
      {children}
    </Field>
  )
}

type Callback<T> = (value?: T) => void
type DispatchWithCallback<T> = (value: T, callback?: Callback<T>) => void

function useStateCallback<T>(initialState: T | (() => T)): [T, DispatchWithCallback<SetStateAction<T>>] {
  const [state, _setState] = useState(initialState)

  const callbackRef = useRef<Callback<T>>()
  const isFirstCallbackCall = useRef(true)

  const setState = useCallback((setStateAction: SetStateAction<T>, callback?: Callback<T>): void => {
    callbackRef.current = callback
    _setState(setStateAction)
  }, [])

  useEffect(() => {
    if (isFirstCallbackCall.current) {
      isFirstCallbackCall.current = false
      return
    }
    callbackRef.current?.(state)
  }, [state])

  return [state, setState]
}

export const NodeDetailsContent = (props: NodeDetailsContentProps): JSX.Element => {
  const {node, processId, dynamicParameterDefinitions, processDefinitionData} = props
  const parameterDefinitions = useMemo(() => {
    return getParameterDefinitions(processDefinitionData, node, dynamicParameterDefinitions)
  }, [dynamicParameterDefinitions, node, processDefinitionData])

  const [originalNode] = useState(node)
  const [editedNode, setEditedNode] = useStateCallback<NodeType>(node)

  return (
    <>
      <NodeDetailsContent2
        {...props}
        {...{
          parameterDefinitions,
          originalNode,
          editedNode,
          setEditedNode,
        }}
      />
      <NodeAdditionalInfoBox node={editedNode} processId={processId}/>
    </>
  )
}

// here `componentDidUpdate` is complicated to clear unsaved changes in modal
export class NodeDetailsContent2 extends React.Component<NodeDetailsContentProps2, State> {
  constructor(props: NodeDetailsContentProps2) {
    super(props)

    const {edges, node, isEditMode, parameterDefinitions} = props
    const {node: editedNode, unusedParameters} = adjustParameters(node, parameterDefinitions)

    this.state = {
      editedNode,
      unusedParameters,
      edges,
    }

    //In most cases this is not needed, as parameter definitions should be present in validation response
    //However, in dynamic cases (as adding new topic/schema version) this can lead to stale parameters
    if (isEditMode) {
      this.updateNodeDataIfNeeded(editedNode)
    }
    this.generateUUID("fields", "parameters")
  }

  generateUUID(...properties: string[]): void {
    properties.forEach((property) => {
      if (has(this.getEditedNode(), property)) {
        get(this.getEditedNode(), property, []).forEach((el) => el.uuid = el.uuid || uuid4())
      }
    })
  }

  //TODO: get rid of this method as deprecated in React
  UNSAFE_componentWillReceiveProps(nextProps: Readonly<NodeDetailsContentProps>): void {
    if (
      !isEqual(this.props.node, nextProps.node) ||
      !isEqual(this.props.edges, nextProps.edges)
    ) {
      this.updateNodeState(() => ({editedNode: nextProps.node}), [])
      //In most cases this is not needed, as parameter definitions should be present in validation response
      //However, in dynamic cases (as adding new topic/schema version) this can lead to stale parameters
      this.updateNodeDataIfNeeded(nextProps.node)
    }
    if (!isEqual(this.props.dynamicParameterDefinitions, nextProps.dynamicParameterDefinitions)) {
      this.adjustStateWithParameters(nextProps.node)
    }
  }

  adjustStateWithParameters(nodeToAdjust: NodeType): void {
    const {node, unusedParameters} = adjustParameters(nodeToAdjust, this.props.parameterDefinitions)
    this.updateNodeState(() => ({editedNode: node}), unusedParameters)
  }

  updateNodeDataIfNeeded(currentNode: NodeType): void {
    if (this.props.isEditMode) {
      this.props.updateNodeData(this.props.processId, {
        variableTypes: this.props.findAvailableVariables(this.props.originalNodeId),
        branchVariableTypes: this.props.findAvailableBranchVariables(this.props.originalNodeId),
        nodeData: currentNode,
        processProperties: this.props.processProperties,
        outgoingEdges: this.state.edges.map(e => ({...e, to: e._id || e.to})),
      })
    } else {
      this.updateNodeState(() => ({editedNode: currentNode}), [])
    }
  }

  componentDidUpdate(prevProps: NodeDetailsContentProps2, prevState: State): void {
    if (
      !isEqual(prevState.editedNode, this.getEditedNode()) ||
      !isEqual(prevState.edges, this.state.edges)
    ) {
      this.updateNodeDataIfNeeded(this.getEditedNode())
    }
  }

  removeElement = (property: string, index: number): void => {
    const {unusedParameters} = this.state
    if (has(this.getEditedNode(), property)) {
      const node = cloneDeep(this.getEditedNode())
      get(node, property).splice(index, 1)

      this.updateNodeState(() => ({editedNode: node}), unusedParameters)
    }
  }

  addElement = <T extends unknown>(property: string, element: T): void => {
    const {unusedParameters} = this.state
    if (has(this.getEditedNode(), property)) {
      const node = cloneDeep(this.getEditedNode())
      get(node, property).push(element)

      this.updateNodeState(() => ({editedNode: node}), unusedParameters)
    }
  }

  idField = (): JSX.Element => (
    <IdField
      isMarked={this.isMarked("id")}
      isEditMode={this.props.isEditMode}
      showValidation={this.props.showValidation}
      editedNode={this.getEditedNode()}
      onChange={(newValue) => this.setNodeDataAt("id", newValue)}
    >
      {this.renderFieldLabel("Name")}
    </IdField>
  )

  customNode = (fieldErrors: Error[], testResultsState: StateForSelectTestResults): JSX.Element => {
    const {
      edges,
    } = this.state
    const {
      originalNode,
      findAvailableVariables,
      originalNodeId,
      node,
      isEditMode,
      showValidation,
      expressionType,
      nodeTypingInfo,
      processDefinitionData,
      showSwitch,
      additionalPropertiesConfig,
      pathsToMark,
    } = this.props

    const {testResultsToShow} = testResultsState

    const variableTypes = findAvailableVariables(originalNodeId)
    //compare window uses legacy egde component
    const isCompareView = pathsToMark !== undefined

    switch (NodeUtils.nodeType(node)) {
      case "Source":
        return this.sourceSinkCommon({fieldErrors, testResultsState})
      case "Sink":
        const toAppend: JSX.Element = (
          <div>
            {this.createField({
              fieldType: FieldType.checkbox,
              fieldLabel: "Disabled",
              fieldProperty: "isDisabled",
            })}
          </div>
        )
        return this.sourceSinkCommon({fieldErrors, children: toAppend, testResultsState})
      case "SubprocessInputDefinition":
        return (
          <SubprocessInputDefinition
            addElement={this.addElement}
            onChange={this.setNodeDataAt}
            node={this.getEditedNode()}
            isMarked={this.isMarked}
            readOnly={!isEditMode}
            removeElement={this.removeElement}
            showValidation={showValidation}
            renderFieldLabel={this.renderFieldLabel}
            errors={fieldErrors}
            variableTypes={variableTypes}
          />
        )
      case "SubprocessOutputDefinition":
        return (
          <SubprocessOutputDefinition
            renderFieldLabel={this.renderFieldLabel}
            removeElement={this.removeElement}
            onChange={this.setNodeDataAt}
            node={this.getEditedNode()}
            addElement={this.addElement}
            isMarked={this.isMarked}
            readOnly={!isEditMode}
            showValidation={showValidation}
            errors={fieldErrors}

            variableTypes={variableTypes}
            expressionType={expressionType || nodeTypingInfo && {fields: nodeTypingInfo}}
          />
        )
      case "Filter":
        return (
          <div className="node-table-body">
            {this.idField()}
            {this.createStaticExpressionField(
              {
                fieldName: "expression",
                fieldLabel: "Expression",
                expressionProperty: "expression",
                fieldErrors: fieldErrors,
                testResultsState,
              }
            )}
            {this.createField({
              fieldType: FieldType.checkbox,
              fieldLabel: "Disabled",
              fieldProperty: "isDisabled",
            })}
            {!isCompareView ?
              (
                <EdgesDndComponent
                  label={"Outputs"}
                  nodeId={originalNodeId}
                  value={edges}
                  onChange={(nextEdges) => this.setEdgesState(nextEdges)}
                  edgeTypes={[
                    {value: EdgeKind.filterTrue, onlyOne: true},
                    {value: EdgeKind.filterFalse, onlyOne: true},
                  ]}
                  readOnly={!isEditMode}
                  fieldErrors={fieldErrors}
                />
              ) :
              null}
            {this.descriptionField()}
          </div>
        )
      case "Enricher":
      case "Processor":
        return (
          <div className="node-table-body">
            {this.idField()}
            {serviceParameters(this.getEditedNode()).map((param, index) => {
              return (
                <div className="node-block" key={node.id + param.name + index}>
                  {this.createParameterExpressionField(
                    {
                      parameter: param,
                      expressionProperty: "expression",
                      listFieldPath: `service.parameters[${index}]`,
                      fieldErrors: fieldErrors,
                      testResultsState,
                    }
                  )}
                </div>
              )
            })}
            {node.type === "Enricher" ?
              this.createField({
                fieldType: FieldType.input,
                fieldLabel: "Output",
                fieldProperty: "output",
                validators: [errorValidator(fieldErrors, "output")],
              }) :
              null}
            {node.type === "Processor" ?
              this.createField({
                fieldType: FieldType.checkbox,
                fieldLabel: "Disabled",
                fieldProperty: "isDisabled",
              }) :
              null}
            {this.descriptionField()}
          </div>
        )
      case "SubprocessInput":
        return (
          <div className="node-table-body">
            {this.idField()}
            {this.createField({
              fieldType: FieldType.checkbox,
              fieldLabel: "Disabled",
              fieldProperty: "isDisabled",
            })}
            <ParameterList
              processDefinitionData={processDefinitionData}
              editedNode={this.getEditedNode()}
              savedNode={this.getEditedNode()}
              setNodeState={newParams => this.setNodeDataAt("ref.parameters", newParams)}
              createListField={(param, index) => this.createParameterExpressionField(
                {
                  parameter: param,
                  expressionProperty: "expression",
                  listFieldPath: `ref.parameters[${index}]`,
                  fieldErrors: fieldErrors,
                  testResultsState,
                }
              )}
              createReadOnlyField={params => (
                <div className="node-row">
                  {this.renderFieldLabel(params.name)}
                  <div className="node-value">
                    <InputWithFocus
                      type="text"
                      className="node-input"
                      value={params.expression.expression}
                      disabled={true}
                    />
                  </div>
                </div>
              )}
            />
            {this.descriptionField()}
          </div>
        )

      case "Join":
      case "CustomNode":
        return (
          <div className="node-table-body">
            {this.idField()}
            {
              hasOutputVar(node, processDefinitionData) && this.createField({
                fieldType: FieldType.input,
                fieldLabel: "Output variable name",
                fieldProperty: "outputVar",
                validators: [errorValidator(fieldErrors, "outputVar")],
              })
            }
            {NodeUtils.nodeIsJoin(this.getEditedNode()) && (
              <BranchParameters
                node={this.getEditedNode()}
                isMarked={this.isMarked}
                showValidation={showValidation}
                showSwitch={showSwitch}
                isEditMode={isEditMode}
                errors={fieldErrors}
                parameterDefinitions={this.props.parameterDefinitions}
                setNodeDataAt={this.setNodeDataAt}
                testResultsToShow={testResultsToShow}
                findAvailableVariables={findAvailableVariables}
              />
            )}
            {this.getEditedNode().parameters?.map((param, index) => {
              return (
                <div className="node-block" key={node.id + param.name + index}>
                  {this.createParameterExpressionField(
                    {
                      parameter: param,
                      expressionProperty: "expression",
                      listFieldPath: `parameters[${index}]`,
                      fieldErrors: fieldErrors,
                      testResultsState,
                    }
                  )}
                </div>
              )
            })}
            {this.descriptionField()}
          </div>
        )
      case "VariableBuilder":
        return (
          <MapVariable
            renderFieldLabel={this.renderFieldLabel}
            removeElement={this.removeElement}
            onChange={this.setNodeDataAt}
            node={this.getEditedNode()}
            addElement={this.addElement}
            isMarked={this.isMarked}
            readOnly={!isEditMode}
            showValidation={showValidation}
            variableTypes={variableTypes}
            errors={fieldErrors}
            expressionType={expressionType || nodeTypingInfo && {fields: nodeTypingInfo}}
          />
        )
      case "Variable":
        const varExprType = expressionType || (nodeTypingInfo || {})[DEFAULT_EXPRESSION_ID]
        return (
          <Variable
            renderFieldLabel={this.renderFieldLabel}
            onChange={this.setNodeDataAt}
            node={this.getEditedNode()}
            isMarked={this.isMarked}
            readOnly={!isEditMode}
            showValidation={showValidation}
            variableTypes={variableTypes}
            errors={fieldErrors}
            inferredVariableType={ProcessUtils.humanReadableType(varExprType)}
          />
        )
      case "Switch":
        const {node: definition} = processDefinitionData.componentGroups?.flatMap(g => g.components).find(c => c.node.type === this.getEditedNode().type)
        const currentExpression = originalNode["expression"]
        const currentExprVal = originalNode["exprVal"]
        const exprValValidator = errorValidator(fieldErrors, "exprVal")
        const showExpression = definition["expression"] ? !isEqual(definition["expression"], currentExpression) : currentExpression?.expression
        const showExprVal = !exprValValidator.isValid() || definition["exprVal"] ? definition["exprVal"] !== currentExprVal : currentExprVal
        return (
          <div className="node-table-body">
            {this.idField()}
            {showExpression ?
              this.createStaticExpressionField({
                fieldName: "expression",
                fieldLabel: "Expression (deprecated)",
                expressionProperty: "expression",
                fieldErrors: fieldErrors,
                testResultsState,
              }) :
              null}
            {showExprVal ?
              this.createField({
                fieldType: FieldType.input,
                fieldLabel: "exprVal (deprecated)",
                fieldProperty: "exprVal",
                validators: [errorValidator(fieldErrors, "exprVal")],
              }) :
              null}
            {!isCompareView ?
              (
                <EdgesDndComponent
                  label={"Conditions"}
                  nodeId={originalNodeId}
                  value={edges}
                  onChange={(nextEdges) => this.setEdgesState(nextEdges)}
                  edgeTypes={[
                    {value: EdgeKind.switchNext},
                    {value: EdgeKind.switchDefault, onlyOne: true, disabled: true},
                  ]}
                  ordered
                  readOnly={!isEditMode}
                  variableTypes={this.getEditedNode()["exprVal"] ?
                    {
                      ...variableTypes,
                      [this.getEditedNode()["exprVal"]]: expressionType || nodeTypingInfo && {fields: nodeTypingInfo},
                    } :
                    variableTypes}
                  fieldErrors={fieldErrors}
                />
              ) :
              null}
            {this.descriptionField()}
          </div>
        )
      case "Split":
        return (
          <div className="node-table-body">
            {this.idField()}
            {this.descriptionField()}
          </div>
        )
      case "Properties":
        const type = node.typeSpecificProperties.type
        //fixme move this configuration to some better place?
        const fields =
          node.isSubprocess ?
            [
              this.createField({
                fieldType: FieldType.input,
                fieldLabel: "Documentation url",
                fieldProperty: "typeSpecificProperties.docsUrl",
                autoFocus: true,
                validators: [errorValidator(fieldErrors, "docsUrl")],
              }),
            ] :
            type === "StreamMetaData" ?
              [
                this.createField({
                  fieldType: FieldType.input,
                  fieldLabel: "Parallelism",
                  fieldProperty: "typeSpecificProperties.parallelism",
                  autoFocus: true,
                  validators: [errorValidator(fieldErrors, "parallelism")],
                }),
                this.createField({
                  fieldType: FieldType.input,
                  fieldLabel: "Checkpoint interval in seconds",
                  fieldProperty: "typeSpecificProperties.checkpointIntervalInSeconds",
                  validators: [errorValidator(fieldErrors, "checkpointIntervalInSeconds")],
                }),
                this.createField({
                  fieldType: FieldType.checkbox,
                  fieldLabel: "Spill state to disk",
                  fieldProperty: "typeSpecificProperties.spillStateToDisk",
                  validators: [errorValidator(fieldErrors, "spillStateToDisk")],
                }),
                this.createField({
                  fieldType: FieldType.checkbox,
                  fieldLabel: "Should use async interpretation",
                  fieldProperty: "typeSpecificProperties.useAsyncInterpretation",
                  validators: [errorValidator(fieldErrors, "useAsyncInterpretation")],
                  defaultValue: processDefinitionData.defaultAsyncInterpretation,
                }),
              ] :
              type === "LiteStreamMetaData" ?
                [
                  this.createField({
                    fieldType: FieldType.input,
                    fieldLabel: "Parallelism",
                    fieldProperty: "typeSpecificProperties.parallelism",
                    autoFocus: true,
                    validators: [errorValidator(fieldErrors, "parallelism")],
                  }),
                ] :
                [this.createField({
                  fieldType: FieldType.input,
                  fieldLabel: "Query path",
                  fieldProperty: "typeSpecificProperties.path",
                  validators: [errorValidator(fieldErrors, "path")],
                })]
        //we sort by name, to have predictable order of properties (should be replaced by defining order in configuration)
        const additionalFields = sortBy(Object.entries(additionalPropertiesConfig), e => e[0]).map(
          ([propName, propConfig]) => (
            <AdditionalProperty
              key={propName}
              showSwitch={showSwitch}
              showValidation={showValidation}
              propertyName={propName}
              propertyConfig={propConfig}
              propertyErrors={fieldErrors}
              onChange={this.setNodeDataAt}
              renderFieldLabel={this.renderFieldLabel}
              editedNode={this.getEditedNode()}
              readOnly={!isEditMode}
            />
          )
        )
        return (
          <div className="node-table-body">
            {this.idField()}
            {[...fields, ...additionalFields]}
            {this.descriptionField()}
          </div>
        )
      default:
        return (
          <div>
            Node type not known.
            <NodeDetails node={node}/>
          </div>
        )
    }
  }

  sourceSinkCommon({
    fieldErrors,
    children,
    testResultsState,
  }: { fieldErrors: Error[], children?: JSX.Element, testResultsState: StateForSelectTestResults }): JSX.Element {
    const {node} = this.props
    return (
      <div className="node-table-body">
        {this.idField()}
        {refParameters(this.getEditedNode()).map((param, index) => (
          <div className="node-block" key={node.id + param.name + index}>
            {this.createParameterExpressionField(
              {
                parameter: param,
                expressionProperty: "expression",
                listFieldPath: `ref.parameters[${index}]`,
                fieldErrors: fieldErrors,
                testResultsState,
              }
            )}
          </div>
        ))}
        {children}
        {this.descriptionField()}
      </div>
    )
  }

  createField = <K extends keyof State["editedNode"] & string, T extends State["editedNode"][K]>({
    fieldType,
    fieldLabel,
    fieldProperty,
    autoFocus,
    readonly,
    defaultValue,
    validators = [],
  }: {
    fieldType: FieldType,
    fieldLabel: string,
    fieldProperty: K,
    autoFocus?: boolean,
    readonly?: boolean,
    defaultValue?: T,
    validators?: Validator[],
  }): JSX.Element => {
    const {isEditMode, showValidation} = this.props
    const isMarked = this.isMarked(fieldProperty)
    const readOnly = !isEditMode || readonly
    const value: T = get(this.getEditedNode(), fieldProperty, null) ?? defaultValue
    const className = !showValidation || allValid(validators, [value]) ? "node-input" : "node-input node-input-with-error"
    const onChange = (newValue) => this.setNodeDataAt(fieldProperty, newValue, defaultValue)
    return (
      <Field
        type={fieldType}
        isMarked={isMarked}
        readOnly={readOnly}
        showValidation={showValidation}
        autoFocus={autoFocus}
        className={className}
        validators={validators}
        value={value}
        onChange={onChange}
      >
        {this.renderFieldLabel(fieldLabel)}
      </Field>
    )
  }

  //this is for "static" fields like expressions in filters, switches etc.
  createStaticExpressionField = ({
    fieldName,
    fieldLabel,
    expressionProperty,
    fieldErrors,
    testResultsState,
  }: { fieldName: string, fieldLabel: string, expressionProperty: string, fieldErrors: Error[], testResultsState?: StateForSelectTestResults }): JSX.Element => {
    return (
      <ExpressionField
        fieldName={fieldName}
        fieldLabel={fieldLabel}
        exprPath={`${expressionProperty}`}
        isEditMode={this.props.isEditMode}
        editedNode={this.getEditedNode()}
        isMarked={this.isMarked}
        showValidation={this.props.showValidation}
        showSwitch={this.props.showSwitch}
        parameterDefinition={findParamDefinitionByName(this.props.parameterDefinitions, fieldName)}
        setNodeDataAt={this.setNodeDataAt}
        testResultsToShow={testResultsState.testResultsToShow}
        renderFieldLabel={this.renderFieldLabel}
        variableTypes={this.props.findAvailableVariables(this.props.originalNodeId, undefined)}
        errors={fieldErrors}
      />
    )
  }

  //this is for "dynamic" parameters in sources, sinks, services etc.
  createParameterExpressionField = ({
    parameter,
    expressionProperty,
    listFieldPath,
    fieldErrors,
    testResultsState,
  }: { parameter: Parameter, expressionProperty: string, listFieldPath: string, fieldErrors: Error[], testResultsState: StateForSelectTestResults }): JSX.Element => {
    return (
      <ExpressionField
        fieldName={parameter.name}
        fieldLabel={parameter.name}
        exprPath={`${listFieldPath}.${expressionProperty}`}
        isEditMode={this.props.isEditMode}
        editedNode={this.getEditedNode()}
        isMarked={this.isMarked}
        showValidation={this.props.showValidation}
        showSwitch={this.props.showSwitch}
        parameterDefinition={findParamDefinitionByName(this.props.parameterDefinitions, parameter.name)}
        setNodeDataAt={this.setNodeDataAt}
        testResultsToShow={testResultsState.testResultsToShow}
        renderFieldLabel={this.renderFieldLabel}
        variableTypes={this.props.findAvailableVariables(this.props.originalNodeId, this.props.parameterDefinitions?.find(p => p.name === parameter.name))}
        errors={fieldErrors}
      />
    )
  }

  publishNodeChange = (): void => {
    this.props.onChange?.(this.getEditedNode(), this.state.edges)
  }

  isMarked = (path: string): boolean => {
    const {pathsToMark} = this.props
    return pathsToMark?.some(toMark => startsWith(toMark, path))
  }

  setNodeDataAt = <T extends unknown>(propToMutate: string, newValue: T, defaultValue?: T): void => {
    const value = newValue == null && defaultValue != undefined ? defaultValue : newValue

    this.updateNodeOnly((current) => {
      const node = cloneDeep(current)
      return {editedNode: set(node, propToMutate, value)}
    })
  }

  updateNodeOnly = (updateNode: (current: NodeType) => { editedNode: NodeType }): void => {
    this.props.setEditedNode(
      node => updateNode(node)?.editedNode,
      this.publishNodeChange
    )
    this.setState(
      s => updateNode(s.editedNode),
      this.publishNodeChange
    )
  }

  updateNodeState = (updateNode: (current: NodeType) => { editedNode: NodeType }, unusedParameters?: Parameter[]): void => {
    if (unusedParameters) {
      this.setState(
        {unusedParameters},
        () => this.updateNodeOnly(updateNode)
      )
    } else {
      this.updateNodeOnly(updateNode)
    }
  }

  descriptionField = (): JSX.Element => {
    return this.createField({
      fieldType: FieldType.plainTextarea,
      fieldLabel: "Description",
      fieldProperty: "additionalFields.description",
    })
  }

  render(): JSX.Element {
    const {currentErrors = [], processId, node} = this.props
    const [fieldErrors, otherErrors] = partition(currentErrors, error => !!error.fieldName)

    return (
      <>
        <NodeErrors errors={otherErrors} message="Node has errors"/>
        <TestResultsWrapper nodeId={node.id}>
          {testResultsState => this.customNode(fieldErrors, testResultsState || {})}
        </TestResultsWrapper>
        <NodeAdditionalInfoBox node={this.getEditedNode()} processId={processId}/>
      </>
    )
  }

  renderFieldLabel = (paramName: string): JSX.Element => (
    <FieldLabel
      nodeId={this.props.originalNodeId}
      parameterDefinitions={this.props.parameterDefinitions}
      paramName={paramName}
    />
  )

  private getEditedNode() {
    return this.state.editedNode
  }

  private setEdgesState = (nextEdges: Edge[]) => {
    this.setState(
      ({edges}) => {
        if (nextEdges !== edges) {
          return {edges: nextEdges}
        }
      },
      this.publishNodeChange
    )
  }
}
