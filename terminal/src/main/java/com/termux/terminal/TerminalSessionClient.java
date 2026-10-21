package com.termux.terminal;

/**
 * Aurora adaptation: emulator callbacks only. Process/session ownership belongs to Aurora.
 * Removed upstream callbacks requiring Termux TerminalSession; retained emulator/log API.
 */
public interface TerminalSessionClient {

    void onTerminalCursorStateChange(boolean state);



    Integer getTerminalCursorStyle();



    void logError(String tag, String message);

    void logWarn(String tag, String message);

    void logInfo(String tag, String message);

    void logDebug(String tag, String message);

    void logVerbose(String tag, String message);

    void logStackTraceWithMessage(String tag, String message, Exception e);

    void logStackTrace(String tag, Exception e);

}
