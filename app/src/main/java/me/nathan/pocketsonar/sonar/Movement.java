package me.nathan.pocketsonar.sonar;

public class Movement {

    public double frequency;
    public double magnitude;
    public int index;
    public MovementType type;

    public Movement(double f, double m, int i, MovementType t) {
        frequency = f;
        magnitude = m;
        index = i;
        type = t;
    }

    public enum MovementType {
        HAND, BASEBALL
    }
}
