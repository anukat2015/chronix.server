package de.qaware.chronix.solr.query.analysis.functions.transformation;

import de.qaware.chronix.solr.query.analysis.functions.ChronixTransformation;
import de.qaware.chronix.solr.query.analysis.functions.ChronixTransformationType;
import de.qaware.chronix.timeseries.MetricTimeSeries;
import de.qaware.chronix.timeseries.dt.DoubleList;
import de.qaware.chronix.timeseries.dt.LongList;

/**
 * This transformation does a vectorization of the time series by removing some points.
 *
 * @author f.lautenschlager
 */
public class Vectorization implements ChronixTransformation<MetricTimeSeries> {

    /**
     * Todo: Describe the algorithm, a bit.
     * <p>
     * Note: The transformation changes the values of the time series!
     * Further analyses such as aggregations uses the transformed values for the calculation.
     *
     * @param timeSeries the time series that is transformed
     * @return a vectorized time series
     */
    @Override
    public MetricTimeSeries transform(MetricTimeSeries timeSeries) {


        int size = timeSeries.size();
        byte[] use_point = new byte[size];

        long[] rawTimeStamps = timeSeries.getTimestampsAsArray();
        double[] rawValues = timeSeries.getValuesAsArray();

        float TOLERANCE = 0.01f;
        compute(rawTimeStamps, rawValues, use_point, TOLERANCE);

        LongList vectorizedTimeStamps = new LongList();
        DoubleList vectorizedValues = new DoubleList();
        for (int i = 0; i < size; i++) {
            //Value is not vectorized
            if (use_point[i] != 0) {
                vectorizedTimeStamps.add(rawTimeStamps[i]);
                vectorizedValues.add(rawValues[i]);
            }
        }

        return new MetricTimeSeries.Builder(timeSeries.getMetric())
                .attributes(timeSeries.attributes())
                .points(vectorizedTimeStamps, vectorizedValues)
                .build();
    }

    /**
     * Calculates the distance between a point and a line.
     * The distance function is defined as:
     * <p>
     * <code>
     * (Ay-Cy)(Bx-Ax)-(Ax-Cx)(By-Ay)</p>
     * s = -----------------------------</p>
     * L^2</p>
     * </code>
     * Then the distance from C to P = |s|*L.
     */
    private double get_distance(long p_x, double p_y, long a_x, double a_y, long b_x, double b_y) {

        double l_2 = (b_x - a_x) * (b_x - a_x) + (b_y - a_y) * (b_y - a_y);
        double s = ((a_y - p_y) * (b_x - a_x) - (a_x - p_x) * (b_y - a_y)) / (l_2);

        return Math.abs(s) * Math.sqrt(l_2);
    }

    private void compute(long[] timestamps, double[] values, byte[] use_point, float tolerance) {

        // do not simplify if not at least 3 points are available
        if (timestamps.length == 3) {
            return;
        }

        int ix_a = 0;
        int ix_b = 1;
        use_point[ix_a] = 1;
        use_point[ix_b] = 1;
        for (int i = 2; i < timestamps.length; i++) {
            double dist = get_distance(timestamps[i], values[i], timestamps[ix_a], values[ix_a], timestamps[ix_b], values[ix_b]);
            if (dist < tolerance) {
                use_point[i - 1] = 0;
                use_point[i] = 1;
            } else {

                // do not continue if not at least one more point is available
                if (i + 1 >= timestamps.length) {
                    for (int j = i; j < timestamps.length; j++) {
                        use_point[j] = 1;
                    }
                    return;
                }

                ix_a = i;
                ix_b = i + 1;
                use_point[ix_a] = 1;
                use_point[ix_b] = 1;
                i++; // continue with next point
            }
        }
    }

    @Override
    public ChronixTransformationType getType() {
        return ChronixTransformationType.VECTOR;
    }
}
