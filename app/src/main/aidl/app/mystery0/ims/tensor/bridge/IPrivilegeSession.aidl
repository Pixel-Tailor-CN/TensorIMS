package app.mystery0.ims.tensor.bridge;
interface IPrivilegeSession {
    void beginDelegation(String operationId);
    void endDelegation(String operationId);
    int getSlotIndex(String operationId, int subId);
    void resetIms(String operationId, int slotIndex);
    boolean isImsRegistered(String operationId, int subId);
}
