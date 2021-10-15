package com.pty4j.windows.conpty;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinDef;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

final class ProcessUtils {

  public static @NotNull WinBase.PROCESS_INFORMATION startProcess(@NotNull PseudoConsole pseudoConsole,
                                                                  @NotNull String commandLine) throws IOException {
    WinEx.STARTUPINFOEX startupInfo = ProcessUtils.prepareStartupInformation(pseudoConsole);
    return ProcessUtils.start(startupInfo, commandLine);
  }

  private static WinEx.STARTUPINFOEX prepareStartupInformation(@NotNull PseudoConsole pseudoConsole) throws IOException {
    WinEx.STARTUPINFOEX startupInfo = new WinEx.STARTUPINFOEX();
    startupInfo.StartupInfo.cb = new WinDef.DWORD(startupInfo.size());
    startupInfo.StartupInfo.lpReserved2 = null; // should be NULL according to the spec, but auto-generated by JNA
    startupInfo.StartupInfo.hStdOutput = null;
    startupInfo.StartupInfo.hStdError = null;
    startupInfo.StartupInfo.hStdInput = null;
    // according to https://github.com/microsoft/terminal/issues/11276#issuecomment-923210023
    startupInfo.StartupInfo.dwFlags = WinBase.STARTF_USESTDHANDLES;

    WinEx.SIZE_TByReference bytesRequired = new WinEx.SIZE_TByReference();
    if (Kernel32Ex.INSTANCE.InitializeProcThreadAttributeList(
        null,
        new WinDef.DWORD(1),
        new WinDef.DWORD(0),
        bytesRequired)) {
      throw new IllegalStateException("InitializeProcThreadAttributeList was unexpected to succeed");
    }

    Memory threadAttributeList = new Memory(bytesRequired.getValue().intValue());
    threadAttributeList.clear();

    startupInfo.lpAttributeList = threadAttributeList;

    if (!Kernel32Ex.INSTANCE.InitializeProcThreadAttributeList(
        threadAttributeList,
        new WinDef.DWORD(1),
        new WinDef.DWORD(0),
        bytesRequired)) {
      throw new LastErrorExceptionEx("InitializeProcThreadAttributeList");
    }

    if (!Kernel32Ex.INSTANCE.UpdateProcThreadAttribute(
        threadAttributeList,
        new WinDef.DWORD(0),
        new BaseTSD.DWORD_PTR(Kernel32Ex.PROC_THREAD_ATTRIBUTE_PSEUDOCONSOLE),
        new WinDef.PVOID(pseudoConsole.getHandle().getPointer()),
        new BaseTSD.SIZE_T(Native.POINTER_SIZE),
        null,
        null)) {
      throw new LastErrorExceptionEx("UpdateProcThreadAttribute");
    }

    return startupInfo;
  }

  private static WinBase.PROCESS_INFORMATION start(WinEx.STARTUPINFOEX startupInfo, String commandLine) throws IOException {
    WinBase.PROCESS_INFORMATION processInfo = new WinBase.PROCESS_INFORMATION();
    if (!Kernel32Ex.INSTANCE.CreateProcessW(
        null,
        (commandLine + '\0').toCharArray(),
        null,
        null,
        false,
        new WinDef.DWORD(Kernel32.EXTENDED_STARTUPINFO_PRESENT),
        null,
        null,
        startupInfo,
        processInfo)) {
      throw new LastErrorExceptionEx("CreateProcessW");
    }
    return processInfo;
  }

  public static void closeHandles(WinBase.PROCESS_INFORMATION processInformation) throws IOException {
    if (!Kernel32.INSTANCE.CloseHandle(processInformation.hThread)) {
      throw new LastErrorExceptionEx("CloseHandle hThread");
    }
    if (!Kernel32.INSTANCE.CloseHandle(processInformation.hProcess)) {
      throw new LastErrorExceptionEx("CloseHandle hProcess");
    }
  }
}