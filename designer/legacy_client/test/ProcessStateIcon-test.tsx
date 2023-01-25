import React from "react"
import Enzyme, {mount} from "enzyme"
import Adapter from "@wojtekmaj/enzyme-adapter-react-17"
import ProcessStateIcon from "../src/components/Process/ProcessStateIcon"
import {unknownTooltip} from "../src/components/Process/messages"
import {absoluteBePath} from "../src/common/UrlUtils"
import {describe, expect, it} from "@jest/globals"

//TODO: In future we shoulde convert it to ts - now, we have some problems with this..

const processState = {
  allowedActions: ["DEPLOY"],
  attributes: null,
  deploymentId: null,
  errorMessage: null,
  icon: "/states/stopping-success.svg",
  startTime: null,
  status: {type: "StoppedStateStatus", name: "CANCELED"},
  tooltip: "The scenario has been successfully cancelled."
}

const noDataProcessState = {
  allowedActions: ["DEPLOY"],
  attributes: null,
  deploymentId: null,
  errorMessage: null,
  icon: null,
  startTime: null,
  status: {type: "StoppedStateStatus", name: "CANCELED"}
}

describe("ProcessStateIcon tests", () => {
  Enzyme.configure({adapter: new Adapter()})

  it("should show defaults for missing process.state and stateProcess", () => {
    const process = {processingType: "streaming"}
    const listState = mount(
      <ProcessStateIcon process={process as any}/>
    )
    expect(listState.find("img").prop("title")).toBe(unknownTooltip())
    expect(listState.find("img").prop("src")).toBe(absoluteBePath("/assets/states/status-unknown.svg"))
  })

  it("should show defaults for loaded process.state without data", () => {
    const process = {processingType: "streaming", state: noDataProcessState}
    const listState = mount(
      <ProcessStateIcon process={process as any}/>
    )
    expect(listState.find("img").prop("title")).toBe(unknownTooltip())
    expect(listState.find("img").prop("src")).toBe(absoluteBePath("/assets/states/status-unknown.svg"))
  })

  it("should show data from loaded process.state", () => {
    const process = {processingType: "streaming", state: processState}
    const listState = mount(
      <ProcessStateIcon process={process as any}/>
    )
    expect(listState.find("img").prop("title")).toBe(processState.tooltip)
    expect(listState.find("img").prop("src")).toBe(absoluteBePath(processState.icon))
  })

  it("should show defaults if loadedProcess is null ", () => {
    const process = {processingType: "streaming", state: processState}
    const listState = mount(
      <ProcessStateIcon process={process as any} isStateLoaded={true}/>
    )
    expect(listState.find("img").prop("title")).toBe(unknownTooltip())
    expect(listState.find("img").prop("src")).toBe(absoluteBePath("/assets/states/status-unknown.svg"))
  })

  it("should show defaults if loadedProcess is empty ", () => {
    const process = {processingType: "streaming", state: processState}
    const listState = mount(
      <ProcessStateIcon
        process={process as any}
        processState={noDataProcessState as any}
        isStateLoaded={true}
      />
    )
    expect(listState.find("img").prop("title")).toBe(unknownTooltip())
    expect(listState.find("img").prop("src")).toBe(absoluteBePath("/assets/states/status-unknown.svg"))
  })

  it("should show loadedProcess data ", () => {
    const listState = mount(
      <ProcessStateIcon
        process={noDataProcessState as any}
        processState={processState as any}
        isStateLoaded={true}
      />
    )
    expect(listState.find("img").prop("title")).toBe(processState.tooltip)
    expect(listState.find("img").prop("src")).toBe(absoluteBePath(processState.icon))
  })
})
