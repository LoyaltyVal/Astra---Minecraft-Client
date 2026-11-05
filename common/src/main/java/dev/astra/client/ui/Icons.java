package dev.astra.client.ui;

import dev.astra.client.util.Render;

/**
 * Small pixel icons for menu buttons (title screen, Right Shift menu), drawn from 9x9 grids so
 * they stay crisp at any GUI scale. '#' is a filled pixel.
 */
public final class Icons {

    public static final String[] PERSON = {
            "...###...",
            "..#####..",
            "..#####..",
            "...###...",
            ".........",
            ".#######.",
            "#########",
            "#########",
            "#########"};

    public static final String[] PEOPLE = {
            ".##...##.",
            "####.####",
            "####.####",
            ".##...##.",
            ".........",
            "###...###",
            "####.####",
            "####.####",
            "........."};

    public static final String[] SHIRT = {
            ".##...##.",
            "####.####",
            "#########",
            ".#######.",
            "..#####..",
            "..#####..",
            "..#####..",
            "..#####..",
            "........."};

    public static final String[] EMOTE = {
            "...##....",
            "...##..#.",
            ".#.##.#..",
            "..####...",
            "...##....",
            "...##....",
            "..#..#...",
            ".#....#..",
            "#......#."};

    public static final String[] CHAT = {
            ".........",
            ".#######.",
            "#########",
            "##.#.#.##",
            "#########",
            ".#######.",
            "..##.....",
            ".#.......",
            "........."};

    public static final String[] GEAR = {
            "...###...",
            ".#.###.#.",
            ".#######.",
            "####.####",
            "###...###",
            "####.####",
            ".#######.",
            ".#.###.#.",
            "...###..."};

    public static final String[] GLOBE = {
            "..#####..",
            ".#..#..#.",
            "#..#.#..#",
            "#########",
            "#..#.#..#",
            "#########",
            "#..#.#..#",
            ".#..#..#.",
            "..#####.."};

    public static final String[] CAMERA = {
            ".........",
            "...###...",
            "#########",
            "###...###",
            "##.....##",
            "###...###",
            "#########",
            ".........",
            "........."};

    public static final String[] POWER = {
            "....#....",
            ".#..#..#.",
            "#...#...#",
            "#...#...#",
            "#.......#",
            "#.......#",
            ".#.....#.",
            "..#####..",
            "........."};

    public static final String[] EYE = {
            ".........",
            "..#####..",
            ".#.....#.",
            "#..###..#",
            "#..###..#",
            ".#.....#.",
            "..#####..",
            ".........",
            "........."};

    public static final String[] HELP = {
            "..####...",
            ".#....#..",
            "......#..",
            ".....#...",
            "....#....",
            "....#....",
            ".........",
            "....#....",
            "........."};

    public static final String[] SEARCH = {
            ".####....",
            "#....#...",
            "#....#...",
            "#....#...",
            ".####....",
            ".....#...",
            "......#..",
            ".......#.",
            "........."};

    public static final String[] ARROW = {
            ".........",
            "....#....",
            ".....#...",
            "#######..",
            "########.",
            "#######..",
            ".....#...",
            "....#....",
            "........."};

    public static final String[] CARET_DOWN = {
            ".........",
            ".........",
            ".........",
            "#######..",
            ".#####...",
            "..###....",
            "...#.....",
            ".........",
            "........."};

    private Icons() {
    }

    /** Draws an icon with its top-left at (x, y); each grid cell is {@code px} GUI pixels. */
    public static void draw(String[] grid, int x, int y, int px, int argb) {
        for (int row = 0; row < grid.length; row++) {
            String line = grid[row];
            int col = 0;
            while (col < line.length()) {
                if (line.charAt(col) != '#') {
                    col++;
                    continue;
                }
                int start = col;
                while (col < line.length() && line.charAt(col) == '#') {
                    col++;
                }
                Render.rect(x + start * px, y + row * px, (col - start) * px, px, argb);
            }
        }
    }

    /** Draws a 9x9 icon centred on (cx, cy). */
    public static void centered(String[] grid, float cx, float cy, int px, int argb) {
        draw(grid, Math.round(cx - 4.5f * px), Math.round(cy - 4.5f * px), px, argb);
    }
}
