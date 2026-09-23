package com.fighter;

import java.util.Random;
import java.util.Scanner;

/**
 * 游戏主类：负责整场对局的流程控制
 *
 * 职责：
 * 1. 回合循环（玩家先手）
 * 2. 行动结算（命中判定、暴击、防御减伤、反击）
 * 3. 战斗日志与状态栏输出
 * 4. 胜负判定
 *
 * 数值策划集中放在常量区，方便调整平衡性。
 */
public class Game {

    // ==================== 数值策划区 ====================
    /** 暴击概率：20% */
    private static final double CRIT_RATE = 0.20;
    /** 暴击伤害倍率：x1.5 */
    private static final double CRIT_MULTIPLIER = 1.5;
    /** 处于防御状态时触发反击的概率：30% */
    private static final double COUNTER_RATE = 0.30;
    /** 反击伤害 */
    private static final int COUNTER_DAMAGE = 8;
    // ===================================================

    private final Random random = new Random();
    private final Scanner scanner = new Scanner(System.in);

    /** 玩家角色 */
    private Fighter player;
    /** 电脑角色 */
    private Fighter computer;
    /** 当前回合数 */
    private int round;

    public Game() {
        this.player = new Fighter("你");
        this.computer = new Fighter("电脑");
        this.round = 0;
    }

    /**
     * 游戏主循环入口：开一场对局，结束后询问是否再来一局
     */
    public void start() {
        printBanner();
        boolean keepPlaying = true;
        while (keepPlaying) {
            playOneMatch();
            keepPlaying = askRestart();
        }
        System.out.println("感谢游玩，再见！");
        scanner.close();
    }

    /**
     * 进行一场完整对局（从双方满血到分出胜负）
     */
    private void playOneMatch() {
        // 每局重新创建角色，保证血量重置
        player = new Fighter("你");
        computer = new Fighter("电脑");
        round = 0;

        while (player.isAlive() && computer.isAlive()) {
            round++;
            printStatus();

            // ---- 玩家行动（先手） ----
            Skill playerSkill = readPlayerChoice();
            resolveAction(player, computer, playerSkill);
            if (!computer.isAlive()) {
                break; // 电脑倒下，本局结束，无需再让电脑出手
            }

            // ---- 电脑行动（后手） ----
            Skill aiSkill = chooseAiAction();
            resolveAction(computer, player, aiSkill);
        }

        announceWinner();
    }

    /**
     * 结算一次行动：处理命中、暴击、防御、反击，并打印战斗日志
     *
     * @param attacker 行动方
     * @param defender 承受方
     * @param skill    行动方选择的技能
     */
    private void resolveAction(Fighter attacker, Fighter defender, Skill skill) {
        // ---------- 1. 防御：不造成伤害，进入防御姿态 ----------
        if (skill == Skill.DEFEND) {
            attacker.startDefending();
            System.out.printf("◆  %s摆出防御架势，准备迎接下一次攻击！%n", attacker.getName());
            return;
        }

        // ---------- 2. 命中判定 ----------
        if (random.nextDouble() >= skill.getHitRate()) {
            System.out.printf("~  %s使出%s，却被%s侧身躲开了！%n",
                    attacker.getName(), skill.getName(), defender.getName());
            return;
        }

        // ---------- 3. 计算伤害（暴击判定） ----------
        int damage = skill.getDamage();
        boolean crit = random.nextDouble() < CRIT_RATE;
        if (crit) {
            damage = (int) Math.round(damage * CRIT_MULTIPLIER);
        }

        // ---------- 4. 结算伤害（若防御则减半） ----------
        int actual = defender.takeDamage(damage);

        // ---------- 5. 战斗日志 ----------
        String action = pickFlavor(attacker, skill);
        if (crit) {
            System.out.printf("★  %s%s——会心一击！对%s造成 %d 点伤害！%n",
                    attacker.getName(), action, defender.getName(), actual);
        } else if (actual < damage) {
            // 实际伤害低于原始伤害，说明对方刚触发了防御减伤
            System.out.printf("◆  %s%s，%s稳稳格挡，只受到 %d 点伤害！%n",
                    attacker.getName(), action, defender.getName(), actual);
        } else {
            System.out.printf("→  %s%s，对%s造成 %d 点伤害！%n",
                    attacker.getName(), action, defender.getName(), actual);
        }
        // ---------- 6. 反击判定：防御方若处于防御姿态，有概率反击 ----------
        // 注意：上面 takeDamage 已解除防御，这里用"实际伤害 < 原始伤害"判断对方是否刚防御过
        if (actual < damage && defender.isAlive() && random.nextDouble() < COUNTER_RATE) {
            int counterActual = attacker.takeDamage(COUNTER_DAMAGE);
            System.out.printf("↔  %s抓住破绽发起反击！对%s造成 %d 点伤害！%n",
                    defender.getName(), attacker.getName(), counterActual);
        }
    }

    /**
     * 读取玩家输入的行动选择（带输入校验，输错不崩、重输）
     */
    private Skill readPlayerChoice() {
        System.out.println();
        System.out.println("请选择本回合行动：");
        System.out.println("  [1] 普通攻击（伤害 10，必中）");
        System.out.println("  [2] 重击（伤害 20，命中率 80%）");
        System.out.println("  [3] 防御（下次受伤减半，30% 概率反击）");

        while (true) {
            System.out.print("输入 1/2/3 > ");
            String input = scanner.nextLine().trim();
            switch (input) {
                case "1":
                    return Skill.NORMAL_ATTACK;
                case "2":
                    return Skill.HEAVY_STRIKE;
                case "3":
                    return Skill.DEFEND;
                default:
                    System.out.println("输入无效，请输入 1、2 或 3。");
            }
        }
    }

    /**
     * 电脑 AI 选行动：加权随机 + 简单策略
     * 血量低于 30% 时更倾向于防御求生，否则以进攻为主
     */
    private Skill chooseAiAction() {
        double roll = random.nextDouble();
        if (computer.getHp() <= 30) {
            // 残血策略：40% 防御 / 40% 普攻 / 20% 重击
            if (roll < 0.40) {
                return Skill.DEFEND;
            } else if (roll < 0.80) {
                return Skill.NORMAL_ATTACK;
            }
            return Skill.HEAVY_STRIKE;
        } else {
            // 常规策略：45% 普攻 / 35% 重击 / 20% 防御
            if (roll < 0.45) {
                return Skill.NORMAL_ATTACK;
            } else if (roll < 0.80) {
                return Skill.HEAVY_STRIKE;
            }
            return Skill.DEFEND;
        }
    }

    // ==================== 输出相关 ====================

    /** 打印开局横幅 */
    private void printBanner() {
        System.out.println("════════════════════════════════════════");
        System.out.println("          ★ 文 字 格 斗 ★");
        System.out.println("      回合制对战 · 暴击 · 防御反击");
        System.out.println("════════════════════════════════════════");
        System.out.println();
    }

    /** 打印每回合的状态栏（血量、可选行动） */
    private void printStatus() {
        System.out.println();
        System.out.println("════════════════════════════════════════");
        System.out.printf ("—— 第 %d 回合 ——%n", round);
        System.out.println("----------------------------------------");
        System.out.printf ("  %s HP %3d/%d  %s%n",
                padToWidth(player.getName(), 4), player.getHp(),
                Fighter.MAX_HP, player.getHpBar());
        System.out.printf ("  %s HP %3d/%d  %s%n",
                padToWidth(computer.getName(), 4), computer.getHp(),
                Fighter.MAX_HP, computer.getHpBar());
        System.out.println("════════════════════════════════════════");
    }

    /**
     * 按显示宽度补齐名字。
     * 控制台里一个汉字占 2 列，printf 的 %-4s 按字符数补空格会导致
     * "你"和"电脑"对不齐，所以这里按显示宽度手动补。
     */
    private String padToWidth(String name, int targetWidth) {
        int width = name.length() * 2; // 本项目角色名均为汉字
        StringBuilder sb = new StringBuilder(name);
        while (width < targetWidth) {
            sb.append(' ');
            width++;
        }
        return sb.toString();
    }

    /** 宣布胜负 */
    private void announceWinner() {
        System.out.println();
        System.out.println("════════════════════════════════════════");
        Fighter winner = player.isAlive() ? player : computer;
        Fighter loser = player.isAlive() ? computer : player;
        System.out.printf ("★★  经过 %d 回合激战，%s击败了%s，获得胜利！%n",
                round, winner.getName(), loser.getName());
        System.out.printf ("    胜者剩余血量：%d/%d%n", winner.getHp(), Fighter.MAX_HP);
        System.out.println("════════════════════════════════════════");
    }

    /** 询问是否再来一局 */
    private boolean askRestart() {
        System.out.print("再来一局？(y/n) > ");
        String input = scanner.nextLine().trim().toLowerCase();
        return input.equals("y") || input.equals("yes");
    }

    /**
     * 为攻击动作随机挑选一段生动描述，让日志更有画面感
     * （返回值不带标点，由调用方拼接）
     */
    private String pickFlavor(Fighter attacker, Skill skill) {
        if (skill == Skill.NORMAL_ATTACK) {
            String[] flavors = {"一记直拳", "一记飞踢", "一记刺拳", "一记鞭腿"};
            return flavors[random.nextInt(flavors.length)];
        } else {
            String[] flavors = {"一记势大力沉的重拳", "一记全力挥出的重锤", "一记破空而来的横扫"};
            return flavors[random.nextInt(flavors.length)];
        }
    }
}
