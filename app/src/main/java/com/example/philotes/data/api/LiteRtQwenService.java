package com.example.philotes.data.api;

import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * LiteRT(TFLite) model runtime smoke test service.
 *
 * This class validates that a local Qwen TFLite model can be loaded and
 * executed.
 */
public class LiteRtQwenService {
    private static final String TAG = "LiteRtQwenService";

    private final File modelFile;
    private Interpreter interpreter;

    public LiteRtQwenService(File modelFile) {
        this.modelFile = modelFile;
    }

    public void initialize() throws IOException {
        if (interpreter != null) {
            return;
        }

        if (modelFile == null || !modelFile.exists()) {
            throw new IOException(
                    "TFLite model not found: " + (modelFile == null ? "null" : modelFile.getAbsolutePath()));
        }

        MappedByteBuffer modelBuffer = mapFile(modelFile);
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
        interpreter = new Interpreter(modelBuffer, options);
    }

    public String runSmokeTest() throws IOException {
        initialize();

        if (interpreter == null) {
            throw new IOException("Interpreter initialization failed");
        }

        for (int i = 0; i < interpreter.getInputTensorCount(); i++) {
            Tensor tensor = interpreter.getInputTensor(i);
            int[] resizedShape = normalizeShape(tensor.shape());
            interpreter.resizeInput(i, resizedShape, false);
        }

        interpreter.allocateTensors();

        Object[] inputs = new Object[interpreter.getInputTensorCount()];
        for (int i = 0; i < inputs.length; i++) {
            Tensor tensor = interpreter.getInputTensor(i);
            inputs[i] = allocateTensorBuffer(tensor);
        }

        Map<Integer, Object> outputs = new HashMap<>();
        for (int i = 0; i < interpreter.getOutputTensorCount(); i++) {
            Tensor tensor = interpreter.getOutputTensor(i);
            outputs.put(i, allocateTensorBuffer(tensor));
        }

        long startMs = System.currentTimeMillis();
        interpreter.runForMultipleInputsOutputs(inputs, outputs);
        long costMs = System.currentTimeMillis() - startMs;

        String summary = "LiteRT smoke test passed. inputs=" + inputs.length
                + ", outputs=" + outputs.size()
                + ", latencyMs=" + costMs;
        Log.i(TAG, summary);
        return summary;
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
    }

    private static MappedByteBuffer mapFile(File file) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file);
                FileChannel fileChannel = inputStream.getChannel()) {
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, file.length());
        }
    }

    private static int[] normalizeShape(int[] shape) {
        int[] normalized = new int[shape.length];
        for (int i = 0; i < shape.length; i++) {
            normalized[i] = shape[i] <= 0 ? 1 : shape[i];
        }
        return normalized;
    }

    private static ByteBuffer allocateTensorBuffer(Tensor tensor) {
        int bytes = tensor.numBytes();
        if (bytes <= 0) {
            bytes = estimateBytes(tensor.shape(), tensor.dataType());
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytes);
        buffer.order(ByteOrder.nativeOrder());
        return buffer;
    }

    private static int estimateBytes(int[] shape, DataType type) {
        int elementCount = 1;
        for (int dim : shape) {
            elementCount *= Math.max(1, dim);
        }
        return Math.max(1, elementCount * bytesPerElement(type));
    }

    private static int bytesPerElement(DataType type) {
        switch (type) {
            case FLOAT32:
            case INT32:
                return 4;
            case INT64:
                return 8;
            case INT16:
                return 2;
            case BOOL:
            case UINT8:
            case INT8:
            default:
                return 1;
        }
    }
}
