package io.github.asagnc.aurora.root;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import io.github.asagnc.aurora.root.IRootResult;

interface IRootControl {
    Bundle snapshot();
    void start(String id, String conversation, in Bundle request, IRootResult callback);
    void cancelAndWait(String id);
    void closeCommands();
    Bundle openTerminal(String id, String conversation, in Bundle request, IRootResult callback);
    void activateTerminal(String id);
    void stopTerminal(String id);
    void prepareLinux(String operation, IRootResult callback);
    void discardLinux(String operation);
    void startLinuxRun(String id, in Bundle request, IRootResult callback);
    void recoverLinuxRun(String id, in Bundle request, IRootResult callback);
    void linuxFiles(String id, String owner, in Bundle request, IRootResult callback);
    Bundle openLinuxTerminal(String id, in Bundle request, IRootResult callback);
    void maintainLinuxMetadata(String id, in Bundle request, IRootResult callback);
    void capturePackets(String id, String conversation, in Bundle request, IRootResult callback);
    void recoverManagedRun(String id, in Bundle request, IRootResult callback);
    void startManagedRun(String id, in Bundle request, in ParcelFileDescriptor script, in ParcelFileDescriptor bridge, IRootResult callback);
}
