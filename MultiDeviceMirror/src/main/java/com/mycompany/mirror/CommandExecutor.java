package com.mycompany.mirror;

import java.io.*;
import java.util.concurrent.*;

public class CommandExecutor {

    public static String execute(String... command) {
        StringBuilder output = new StringBuilder();

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true); // gabung stdout + stderr

            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            process.waitFor();

        } catch (Exception e) {
            output.append("ERROR: ").append(e.getMessage());
        }

        return output.toString();
    }

    // 🔥 versi dengan timeout (optional tapi recommended)
    public static String executeWithTimeout(long timeoutMs, String... command) {
        StringBuilder output = new StringBuilder();

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            ExecutorService executor = Executors.newSingleThreadExecutor();

            Future<?> future = executor.submit(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (IOException ignored) {}
            });

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);

            if (!finished) {
                process.destroy();
                output.append("TIMEOUT");
            }

            future.get();
            executor.shutdown();

        } catch (Exception e) {
            output.append("ERROR: ").append(e.getMessage());
        }

        return output.toString();
    }
}