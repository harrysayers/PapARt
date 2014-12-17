/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.inria.guimodes;

import java.util.HashMap;
import processing.core.PApplet;

/**
 *
 * @author Jeremy Laviole <jeremy.laviole@inria.fr>
 */
public class Mode {

    private static Mode currentMode;
    private static final HashMap<String, Mode> modes = new HashMap<String, Mode>();
    private static int changeTime;
    private static PApplet parent = null;
    protected String name;
    protected int number;
    private static int NB_MODES = 0;

    public static void init(PApplet papplet) {
        parent = papplet;
    }

    protected static Mode create(String name) {
        int id = NB_MODES++;
        return create(name, id);
    }

    protected static Mode create(String name, int id) {
        Mode mode = new Mode();
        mode.name = name;
        mode.number = id;
        return mode;
    }

    public static int asInt() {
        return currentMode.number;
    }

    public static void clear() {
        modes.clear();
    }

    public static Mode add(String modeName) {
        Mode mode = Mode.create(modeName);
        modes.put(modeName, mode);
        return mode;
    }

    public static Mode add(String modeName, int id) {
        Mode mode = Mode.create(modeName, id);
        modes.put(modeName, mode);
        return mode;
    }
    
    public static boolean is(String modeName) {
        return currentMode == modes.get(modeName);
    }

    public static boolean is(Mode mode) {
        return currentMode == mode;
    }

    public static void set(Mode mode) {
        assert (mode != null);
        assert (contains(mode));
        setCurrentMode(mode);
    }

    public static void set(String modeName) {
        Mode mode = modes.get(modeName);
        assert (mode != null);
        setCurrentMode(mode);
    }

    private static void setCurrentMode(Mode mode) {
        if (parent != null) {
            changeTime = parent.millis();
        }
        currentMode = mode;
    }

    public String name() {
        return this.name;
    }

    public static Mode getCurrent() {
        return currentMode;
    }

    public static String getCurrentName() {
        return currentMode.name();
    }

    public static int size() {
        return modes.size();
    }

    public static boolean contains(String key) {
        return modes.containsKey(key);
    }

    public static boolean contains(Mode value) {
        return modes.containsValue(value);
    }

    public String toString() {
        return modes.toString();
    }

    public static int lastChangeTime() {
        if (parent == null) {
            System.err.println("Use the init() method to use timer capabilites.");
            return 0;
        }
        return changeTime;
    }
}
