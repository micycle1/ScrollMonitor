package micycle.scrollMonitor;

import static processing.core.PApplet.constrain;

import java.util.Arrays;

import processing.core.PVector;

/**
 * Encapsulates Container for data, index, queue position, draw-data etc.
 * Supports smoothing in the form of a moving average. To this end, the length
 * of the data array must be of size= size+smoothingSize so the first data item
 * we want to see can be smoothed, rather than skipping the first smoothingSize
 * items. TODO dynamic ordering / opacity based on mouse-over/highest value TODO
 * max datapoint/history size (purged thereafter) & viewable datapoints,
 * 
 * does not draw the data itself, this is left to scrollmonitor; provides the
 * data only.
 * 
 * @author micycle1
 *
 */
final class DataStream implements Comparable<DataStream> {

	/**
	 * Position in queue
	 */
	int queueIndex; // TODO remove / move to #ScrollMonitor)
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
	int stroke;
	/**
	 * Data smoothing (moving average)
	 */
	int smoothing;
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
	 * Ceiling value of data drawn (doesn't affect data pushed)
	 */
	float maxValue;
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

		data = new float[length + smoothing];
		Arrays.fill(data, -1); // init to -1 so not drawn by stroke

		data[0] = -1.987654f; // mark for inital smoothing history generation
		fillColour = -1232323; // p.color(50, 50, 130, 150); // TODO
		stroke = -12389127; // p.color(255, 80, 180, 100);

		drawData = new float[length];
		Arrays.fill(drawData, -1); // init to -1 so not drawn by stroke

		fill = true;
		outline = true;
		this.drawDimensions = drawDimensions;
	}

	/**
	 * varargs
	 * 
	 * @param datum datapoints
	 */
	void push(float datum) {

		// on the first data point, generate moving average data, and append data to end
		// of the array
		// because the pointer starts at 0, so will it look backwards (wrap around to
		// the end of the array) for history
		if (pointer == 0 && data[0] == -1.987654f) {
			for (int i = 0; i < smoothing; i++) {
				data[length + smoothing - i] = datum;
			}
		}

		data[pointer] = datum; // push raw datum

		float drawData = datum; // sum of moving average
		for (int i = 0; i < smoothing; i++) { // calc moving average
			int newPointer = Math.floorMod(pointer - 1 - i, length + smoothing); // pointer to previous data, can
																					// wrap around
			drawData += data[newPointer]; // sum moving average
		}
		drawData /= (smoothing + 1); // divide to get average

		this.drawData[Math.floorMod(pointer - smoothing - 1, length + smoothing)] = constrain(drawData, 0, maxValue - 1)
				* (drawDimensions.y / maxValue); // constrain & scale (-1 is stroke Weight)

		pointer++; // inc pointer
		// pointer = ((pointer % length) + smoothing) % (length + smoothing); // recalc
		// pointer (offset to active part of data array)
		pointer %= (length + smoothing);
	}

	void pushEmpty() {
		push(-1); // TODO
	}

	/**
	 * Set new smoothing level
	 * 
	 * @param smoothing
	 */
	void setSmoothing(int smoothing) {
		smoothing = constrain(smoothing, 0, length); // floor & ceiling
		if (this.smoothing != smoothing) { // perform if different | TODO fix with smoothing data
			float[] tempData = new float[length + smoothing]; // create new buffer
			System.arraycopy(data, this.smoothing, tempData, smoothing, length); // copy into new buffer
			data = tempData; // replace data with new buffer
			this.smoothing = smoothing; // set new smoothing level

			// TODO recalc drawData
		}
	}

	void setMaxValue(float maxValue) {
		this.maxValue = maxValue;
		// TODO recalc drawvalues w/ smoothing when changed
		for (int i = 0; i < drawData.length; i++) {
			drawData[i] = constrain(data[i], -1, maxValue - 1) * (drawDimensions.y / maxValue); // -1 because of stroke
		}
	}

	void setColor(int color) {
		this.fillColour = color;
	}

	void setActive(boolean active) {
		this.active = active;
	}

	void setUnit(String unit) {
		dataUnit = unit;
	}

	/**
	 * Get draw data that is logically at the index given (where 0 is left most
	 * datapoint) or, ordered by recency, where 0 is oldest data point
	 * 
	 * @param index
	 * @return
	 */
	float getDrawData(int index) {
		if (paused) {
			int i = Math.floorMod(pointerCache + index - 1, length + smoothing); // -1, because pointer is incremented
																					// after push
			return drawDataCache[i];
		} else {
			int i = Math.floorMod(pointer + index - 1, length + smoothing); // -1, because pointer is incremented after
			// push
			return drawData[i];
		}
	}

	/**
	 * Get raw data (data pushed to stream) that is logically at the index given
	 * (where 0 is left most datapoint)
	 * 
	 * @param index
	 * @return
	 */
	float getRawData(int index) {
		if (paused) {
			int i = Math.floorMod(pointerCache - 1 + index - length, length + smoothing);
			return rawDataCache[i];
		}
		else {
			int i = Math.floorMod(pointer - 1 + index - length, length + smoothing);
			return data[i];
		}
	}
	
	void setDrawDimensions(PVector drawDimensions) {
		this.drawDimensions = drawDimensions;
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

	/**
	 * Used to determine iteration order TODO use value of last datapoint to
	 * determine order?
	 */
	@Override
	public int compareTo(DataStream d) {
		if (queueIndex > d.queueIndex)
			return 1;
		else if (queueIndex == d.queueIndex) {
			return 0;
		}
		return -1;
	}
}