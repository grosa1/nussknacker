import React, {useEffect, useState} from "react"
import {useSelector} from "react-redux"
import HttpService from "../../../../http/HttpService"
import {getProcessCounts} from "../../../../reducers/selectors/graph"
import {Process, SubprocessNodeType} from "../../../../types"
import ErrorBoundary from "../../../common/ErrorBoundary"
import NodeUtils from "../../NodeUtils"
import {SubProcessGraph as BareGraph} from "../../SubProcessGraph"

export function SubprocessContent({nodeToDisplay}: { nodeToDisplay: SubprocessNodeType }): JSX.Element {
  const processCounts = useSelector(getProcessCounts)

  const [subprocessContent, setSubprocessContent] = useState<Process>(null)

  useEffect(
    () => {
      if (NodeUtils.nodeIsSubprocess(nodeToDisplay)) {
        const id = nodeToDisplay?.ref.id
        HttpService.fetchProcessDetails(id).then(response => {
          setSubprocessContent(response.data.json)
        })
      }
    },
    [nodeToDisplay],
  )

  const subprocessCounts = (processCounts[nodeToDisplay.id] || {}).subprocessCounts || {}

  return (
    <ErrorBoundary>
      {subprocessContent && (
        <BareGraph
          processCounts={subprocessCounts}
          processToDisplay={subprocessContent}
          nodeIdPrefixForSubprocessTests={`${subprocessContent.id}-`}
        />
      )}
    </ErrorBoundary>
  )
}
