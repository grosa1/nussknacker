import {AnyAction, Reducer as ReduxReducer} from "redux"
import {ThunkAction as TA, ThunkDispatch as TD} from "redux-thunk"

import {ActionTypes} from "./actionTypes"
import {
  DisplayProcessActivityAction,
  HandleHTTPErrorAction,
  LoggedUserAction,
  ReportEventAction,
  UiSettingsAction
} from "./nk"
import {FeatureFlagsActions} from "./nk/featureFlags"
import {RootState} from "../reducers"
import {NotificationActions} from "./nk/notifications"
import {UserSettings} from "../reducers/userSettings"

type TypedAction =
  | HandleHTTPErrorAction
  | ReportEventAction
  | LoggedUserAction
  | UiSettingsAction
  | DisplayProcessActivityAction
  | { type: "UNDO" }
  | { type: "REDO" }
  | { type: "CLEAR" }
  | { type: "JUMP_TO_STATE", direction: "PAST" | "FUTURE", index: number }
  | FeatureFlagsActions
  | { type: "TOGGLE_SETTINGS", settings: Array<keyof UserSettings> }
  | { type: "SET_SETTINGS", settings: UserSettings }
  | NotificationActions

interface UntypedAction extends AnyAction {
  type: Exclude<ActionTypes, TypedAction["type"]>,
}

export type Action = UntypedAction | TypedAction

type State = RootState

export type ThunkAction<R = void, S = State> = TA<R, S, undefined, Action>
export type ThunkDispatch<S = State> = TD<S, undefined, Action>
export type Reducer<S> = ReduxReducer<S, Action>
