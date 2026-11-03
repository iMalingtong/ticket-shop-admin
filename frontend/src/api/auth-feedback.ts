import { ApiError } from "../utils/http";
export function authFeedback(error: unknown): string {
  if (!(error instanceof ApiError)) return "服务暂不可用，请稍后重试";
  switch (error.code) {
    case "INVALID_CREDENTIALS":
      return "账号或密码不正确";
    case "LOGIN_RATE_LIMITED":
      return "尝试次数过多，请稍后再试";
    case "CSRF_INVALID":
    case "CSRF_MISSING":
      return "页面校验已失效，请刷新后重试";
    case "VALIDATION_ERROR":
      return "输入不符合要求；新密码须为12–128个字符，且不能与原密码相同";
    case "ACCOUNT_DISABLED":
      return "账号已停用，请联系管理员";
    case "PASSWORD_CHANGE_REQUIRED":
      return "请先修改初始密码";
    default:
      return error.status === 401
        ? "登录已失效，请重新登录"
        : "服务暂不可用，请稍后重试";
  }
}
export const validPassword = (value: string) =>
  [...value].length >= 12 && [...value].length <= 128;
