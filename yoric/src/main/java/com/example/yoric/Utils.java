package com.example.yoric;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class Utils {

    public static String getProcessName() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream("/proc/self/cmdline")))) {
            String content = reader.readLine();
            return content.trim();
        } catch (IOException e) {
            return null;
        }
    }
}
