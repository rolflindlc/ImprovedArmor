package io.github.rolflindlc.improvedarmor.core;

/**
 * Pure layout of the armor HUD: which source owns each armor point of the bar. Free of Minecraft classes so it can be
 * unit tested.
 *
 * <p>The bar draws one icon per 2 points, 10 icons (20 points) per row, like vanilla. Up to {@code maxRows} rows are
 * stacked; past that the bar collapses to a single row that keeps the proportions of every source, and the HUD prints
 * the total next to it.
 */
public final class ArmorBar {
    public static final int POINTS_PER_ROW = 20;

    private ArmorBar() {
    }

    /**
     * @param owners  for each drawn point (index 0 = first half icon of the bottom row), the index of the source that
     *                owns it
     * @param rows    rows to draw (at least 1)
     * @param compact true when the bar was collapsed to one proportional row
     * @param total   total armor points, rounded to the nearest point
     */
    public record Layout(int[] owners, int rows, boolean compact, int total) {
    }

    /**
     * Assigns every point of the bar to a source. Point {@code i} belongs to the source whose cumulative range contains
     * {@code i + 0.5}, so fractional contributions round fairly and the sources keep their order.
     *
     * @param points  armor points of each source, in drawing order (negative values count as 0)
     * @param maxRows rows allowed before collapsing (at least 1)
     */
    public static Layout layout(double[] points, int maxRows) {
        maxRows = Math.max(1, maxRows);
        double sum = 0;
        for (double v : points) {
            sum += Math.max(0, v);
        }
        int total = (int) Math.round(sum);
        boolean compact = total > maxRows * POINTS_PER_ROW;
        int drawn = compact ? POINTS_PER_ROW : total;
        double scale = sum <= 0 ? 0 : (compact ? POINTS_PER_ROW / sum : drawn / sum);
        int[] owners = new int[drawn];
        int source = 0;
        double end = points.length == 0 ? 0 : Math.max(0, points[0]) * scale;
        for (int i = 0; i < drawn; i++) {
            double mid = i + 0.5;
            while (mid > end && source < points.length - 1) {
                source++;
                end += Math.max(0, points[source]) * scale;
            }
            owners[i] = source;
        }
        int rows = compact ? 1 : Math.max(1, (drawn + POINTS_PER_ROW - 1) / POINTS_PER_ROW);
        return new Layout(owners, rows, compact, total);
    }
}
