package app.mystery0.ims.tensor.bridge;
import android.os.Bundle;
oneway interface IEmbeddedResult {
    void onResult(String operationId, long epoch, in Bundle result);
}
