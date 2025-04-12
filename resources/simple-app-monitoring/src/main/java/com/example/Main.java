package com.example;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;

public class Main {
    public static void main(String[] args)  throws InterruptedException {
        System.out.println(StringUtils.capitalize("Hello world!!"));
        customSleepingThread();
        CustomClassGson.customClassGsonSleep();
        Files.exists(Path.of("file.txt"));
        System.out.println(StringUtils.capitalize("Hello world2!"));
        for (long i =0; i<2; ++i) {
            print();

        }
    }

    static void print() {
        StringUtils.capitalize("Hello world!");
        try { Thread.sleep(10); } catch (InterruptedException e) { }
    }

    private static void customSleepingThread() {
        try { Thread.sleep(4000); } catch (InterruptedException e) { }
    }
}