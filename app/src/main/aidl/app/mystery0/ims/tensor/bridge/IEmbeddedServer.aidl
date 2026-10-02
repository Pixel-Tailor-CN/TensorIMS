package app.mystery0.ims.tensor.bridge;
import android.os.Bundle;
import app.mystery0.ims.tensor.bridge.IEmbeddedResult;
interface IEmbeddedServer {
    Bundle handshake(String challenge, IBinder clientToken);
    void execute(in Bundle request, IEmbeddedResult callback);
    boolean shutdownOwnedServer(String instanceId);
    Bundle getStatus();
}
