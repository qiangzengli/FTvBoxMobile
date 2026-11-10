package com.github.tvbox.osc.server

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader

/**
 * @author pj567
 * @date :2021/1/4
 * @description:
 */
object ShellUtils {
    const val COMMAND_SU = "su"
    const val COMMAND_SH = "sh"
    const val COMMAND_EXIT = "exit\n"
    const val COMMAND_LINE_END = "\n"

    /**
     * 查看是否有了root权限
     *
     * @return
     */
    @JvmStatic
    fun checkRootPermission(): Boolean {
        return execCommand("echo root", true, false).result == 0
    }

    /**
     * 执行shell命令，默认返回结果
     *
     * @param command command
     * @return
     * @see ShellUtils.execCommand
     */
    @JvmStatic
    fun execCommand(command: String, isRoot: Boolean): CommandResult {
        return execCommand(arrayOf(command), isRoot, true)
    }

    /**
     * 执行shell命令，默认返回结果
     *
     * @param commands command list
     * @return
     * @see ShellUtils.execCommand
     */
    @JvmStatic
    fun execCommand(commands: List<String>?, isRoot: Boolean): CommandResult {
        return execCommand(commands?.toTypedArray(), isRoot, true)
    }

    /**
     * 执行shell命令，默认返回结果
     *
     * @param commands command array
     * @return
     * @see ShellUtils.execCommand
     */
    @JvmStatic
    fun execCommand(commands: Array<String>?, isRoot: Boolean): CommandResult {
        return execCommand(commands, isRoot, true)
    }

    /**
     * execute shell command
     *
     * @param command command
     * @param isNeedResultMsg whether need result msg
     * @return
     * @see ShellUtils.execCommand
     */
    @JvmStatic
    fun execCommand(command: String, isRoot: Boolean, isNeedResultMsg: Boolean): CommandResult {
        return execCommand(arrayOf(command), isRoot, isNeedResultMsg)
    }

    /**
     * execute shell commands
     *
     * @param commands command list
     * @return
     * @see ShellUtils.execCommand
     */
    @JvmStatic
    fun execCommand(commands: List<String>?, isRoot: Boolean, isNeedResultMsg: Boolean): CommandResult {
        return execCommand(commands?.toTypedArray(), isRoot, isNeedResultMsg)
    }

    /**
     * execute shell commands
     *
     * @param commands command array
     * @return
     */
    @JvmStatic
    fun execCommand(commands: Array<String>?, isRoot: Boolean, isNeedResultMsg: Boolean): CommandResult {
        var result = -1
        if (commands == null || commands.isEmpty()) {
            return CommandResult(result, null, null)
        }
        var process: Process? = null
        var successResult: BufferedReader? = null
        var errorResult: BufferedReader? = null
        var successMsg: StringBuilder? = null
        var errorMsg: StringBuilder? = null
        var os: DataOutputStream? = null
        try {
            process = Runtime.getRuntime().exec(if (isRoot) COMMAND_SU else COMMAND_SH)
            os = DataOutputStream(process.outputStream)
            for (command in commands) {
                if (command == null) {
                    continue
                }
                os.write(command.toByteArray())
                os.writeBytes(COMMAND_LINE_END)
                os.flush()
            }
            os.writeBytes(COMMAND_EXIT)
            os.flush()
            result = process.waitFor()
            if (isNeedResultMsg) {
                successMsg = StringBuilder()
                errorMsg = StringBuilder()
                successResult = BufferedReader(InputStreamReader(process.inputStream))
                errorResult = BufferedReader(InputStreamReader(process.errorStream))
                var s: String?
                while (successResult.readLine().also { s = it } != null) {
                    successMsg.append(s)
                }
                while (errorResult.readLine().also { s = it } != null) {
                    errorMsg.append(s)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                os?.close()
                successResult?.close()
                errorResult?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            process?.destroy()
        }
        return CommandResult(
            result,
            successMsg?.toString(),
            errorMsg?.toString()
        )
    }

    /**
     * 运行结果
     */
    class CommandResult {
        @JvmField
        var result: Int

        @JvmField
        var successMsg: String? = null

        @JvmField
        var errorMsg: String? = null

        constructor(result: Int) {
            this.result = result
        }

        constructor(result: Int, successMsg: String?, errorMsg: String?) {
            this.result = result
            this.successMsg = successMsg
            this.errorMsg = errorMsg
        }

        override fun toString(): String {
            return "CommandResult{" +
                    "result=" + result +
                    ", successMsg='" + successMsg + '\'' +
                    ", errorMsg='" + errorMsg + '\'' +
                    '}'
        }
    }
}
