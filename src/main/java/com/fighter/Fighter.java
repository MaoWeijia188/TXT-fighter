package com.fighter;

public class Fighter {
    public static final int MAX_HP = 100;
    private static final double DEFEND_REDUCTION = 0.50;
    private final String name;
    private int hp;
    private boolean defending;
    public Fighter(String name) {
        this.name = name;
        this.hp = MAX_HP;
        this.defending = false;
    }
    public int takeDamage(int damage){
        int actual = damage;
        if(defending){
            actual = (int) Math.floor(damage * (1 - DEFEND_REDUCTION));
            defending = false;
        }
    }
}
