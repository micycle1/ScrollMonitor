package micycle.scrollMonitor;

import static processing.core.PApplet.constrain;

import java.util.Arrays;

import processing.core.PVector;

/**
 * A container for data, index, queue position, draw-data, etc. A DataStream
 * does not draw the data itself, this is left to scrollmonitor. Supports
 * smoothing in the form of a moving average.
 * 
 * @author Michael Carleton
 *
 */
final class DataStream {

	/**
	 * DataStream name (identifier)
	 */
	final String name;
	/**
	 * Which index in {@link #data} is the most recent, and where iteration should
	 * start
	 */
	int pointer;
	/**
	 * Fill color when drawn
	 */
	int fillColour;
	/**
	 * Stoke (outline) color
	 */
	int strokeColour;
	/**
	 * Data smoothing (moving average)
	 */
	private int smoothing;
	/**
	 * Data (raw)
	 */
	float[] data;
	/**
	 * Data used to draw points (vals)
	 */
	float[] drawData;
	/**
	 * Can be drawn / data pushed to it?
	 */
	boolean active;
	/**
	 * Fill this or outline only?
	 */
	boolean fill, outline;
	/**
	 * Ceiling value of data drawn (doesn't affect data pushed). Should be accessed
	 * via setMaxValue().
	 */
	private float maxValue;
	/**
	 * Buffer/history size (size of {@link #data})
	 */
	final int length;
	int direction = 1; // 1 scroll to left; -1 scroll to right
	PVector drawDimensions; // used to scale raw data for draw data
	String dataUnit = ""; // optional data label for axis values

	float maxLiveValue; // current maximum value, used for datastream dynamically y axis

	float[] drawDataCache; // returns this when paused
	float[] rawDataCache; // returns this to streaMouseOver when paused
	private int pointerCache;
	/**
	 * When true, stream will return data as it was then the paused flag became true
	 * (drawDataCache)
	 */
	boolean paused = false;
	boolean draw = true; // draw/render this datastream?

	/**
	 * todo auto push negative so it always scrolls? Scrolls to accomdate new data
	 * only vs will always scroll
	 * 
	 * @param name
	 */
	public DataStream(String name, int history, PVector drawDimensions) {
		this.name = name;
		length = history;
		active = true;
		smoothing = 0;
		pointer = 0;

		data = new float[length];
		Arrays.fill(data, -1); // init to -1 so not drawn by stroke

		drawData = new float[length];

		fillColour = -1232323; // p.color(50, 50, 130, 150);
		strokeColour = -12389127; // p.color(255, 80, 180, 100);

		fill = true;
		outline = true;
		this.drawDimensions = drawDimensions;
	}

	public DataStream(String name, int history, PVector drawDimensions, int smoothing) {
		this(name, history, drawDimensions);
		this.smoothing = smoothing;
	}

	/**
	 * Pushes data to the datastream.
	 * 
	 * @param datum
	 */
	void push(float datum) {
		data[pointer] = datum; // push raw datum
		calcDrawData(pointer);
		pointer++; // inc pointer
		pointer %= (length);
	}

	void pushEmpty() {
		push(Float.NEGATIVE_INFINITY); // TODO
	}

	/**
	 * Sets a new smoothing level
	 * 
	 * @param smoothing
	 */
	void setSmoothing(int smoothing) {
		smoothing = constrain(smoothing, 0, length); // floor & ceiling TODO remove
		if (this.smoothing != smoothing) { // perform if different | TODO fix with smoothing data
			this.smoothing = smoothing;
			recalcDrawData();
		}
	}

	void setMaxValue(float maxValue) {
		this.maxValue = maxValue;
		for (int i = 0; i < drawData.length; i++) {
			drawData[i] = constrain(data[i], -1, maxValue - 1) * (drawDimensions.y / maxValue); // -1 because of stroke
		}
	}

	void recalcDrawData() {
		for (int i = 0; i < data.length; i++) {
			calcDrawData(i);
		}
	}

	/**
	 * Calculates draw data for at the current pointer, taking into account
	 * smoothing.
	 */
	private void calcDrawData(int pointer) {
		float drawDatum = data[pointer]; // sum of moving average
		int skippedDataPoints = 0; // number of "empty" points skipped in average calculation
		for (int i = 0; i < smoothing; i++) { // calc moving average
			int newPointer = Math.floorMod(pointer - 1 - i, length); // pointer to previous data
			if (data[newPointer] != Float.NEGATIVE_INFINITY) {
				drawDatum += data[newPointer]; // sum moving average
			} else {
				skippedDataPoints++;
			}
		}
		drawDatum /= (smoothing + 1 - skippedDataPoints); // divide to get average

		// constrain & scale (-1 is stroke Weight)
		drawData[Math.floorMod(pointer - 1, length)] = constrain(drawDatum, 0, maxValue - 1) * (drawDimensions.y / maxValue);
	}

	/**
	 * Gets draw data that is logically at the index given (where 0 is left most
	 * datapoint) or, ordered by recency, where 0 is oldest data point
	 * 
	 * @param index
	 * @return
	 */
	float getDrawData(int index) {
		if (paused) {
			// -1, because pointer is incremented after push
			int i = Math.floorMod(pointerCache + index - 1, length);
			return drawDataCache[i];
		} else {
			int i = Math.floorMod(pointer + index - 1, length);
			return drawData[i];
		}
	}

	/**
	 * Gets raw data (data pushed to stream) that is logically at the index given
	 * (where 0 is left most datapoint)
	 * 
	 * @param index
	 * @return
	 */
	float getRawData(int index) {
		if (paused) {
			int i = Math.floorMod(pointerCache - 1 + index - length, length);
			return rawDataCache[i];
		} else {
			int i = Math.floorMod(pointer - 1 + index - length, length);
			return data[i];
		}
	}

	void pause() {
		drawDataCache = drawData.clone();
		rawDataCache = data.clone();
		paused = true;
		pointerCache = pointer;
	}

	void resume() {
		paused = false;
		drawDataCache = null; // release memory
		rawDataCache = null; // release memory
	}
}