package micycle.scrollMonitor;

import static processing.core.PApplet.constrain;
import static processing.core.PApplet.max;

import java.util.Arrays;
import java.util.HashMap;

import processing.core.PApplet;
import processing.core.PShape;
import processing.core.PVector;

/**
 * Add, push then draw Each graph can contain multiple datastreams. TODO change
 * settings (mouseover etc) + drag + pause + mouseover value + toggle stream
 * visibility+order
 * 
 * TODO: datastream defines how many datapoints are visible from each stream
 * (global) or per-stream?
 * 
 * @author micycle1
 *
 */
public class ScrollMonitor extends ProcessingPane {

	private final HashMap<String, DataStream> streams;
	// private SortedMap<DataStream, Integer> todo; // TODO, ordered iteration

	public ScrollMonitor(PApplet p, PVector position, PVector dimensions) {
		super(p, position, dimensions);
		streams = new HashMap<>();
	}

	public void addDataStream(String name, int historyCap) {
		streams.put(name, new DataStream(name, historyCap));
	}

	public void push(String dataStreamName, float n) {
		streams.get(dataStreamName).push(n);
	}

	/**
	 * Set the level of visual smoothing a stream has using a equal-weighted moving
	 * average of the last n terms TODO INCREASE STREAM SIZE BY SMOOTHING AMOUNT
	 * 
	 * @param dataStreamName
	 * @param smoothing      default = 1 == no smoothing; must be >=1
	 */
	public void setSmoothing(String dataStreamName, int smoothing) {
		streams.get(dataStreamName).setSmoothing(smoothing);
	}

	/**
	 * Max/ceiling display value for stream
	 */
	public void setMaxValue(String dataStream, float value) {
		streams.get(dataStream).setMaxValue(value);
	}

	public void setStreamColour(String dataStream, int color) {
		streams.get(dataStream).setColor(color);
	}

	@Override
	public void draw() {

		canvas.strokeCap(PApplet.ROUND);
		canvas.clear();

		drawBG(streams.values().iterator().next()); // TODO
		int strokeWeight = 3;
		canvas.strokeWeight(strokeWeight);
		for (DataStream d : streams.values()) { // TODO draw back to front
			canvas.stroke(d.stroke);
			PShape graph = p.createShape();
			if (d.fill) {
				graph.setFill(d.fillColour);
			} else {
				graph.noFill();
			}
			if (d.outline) {
				graph.setStrokeWeight(3);
			} else {
				graph.noStroke();
			}
			graph.beginShape();
			graph.stroke(d.stroke);

			graph.vertex(-strokeWeight, (dimensions.y) - d.getDrawData(0)); // start out of bounds
			for (int i = 0; i < d.length; i++) {
				float val = d.getDrawData(i);
				float x = i * (dimensions.x / (d.length - 1)); // calc x coord -- scale to x dimension
				float y = (dimensions.y) - val;
				graph.vertex(x, y);
			}

			graph.vertex(dimensions.x + strokeWeight, (dimensions.y) - d.getDrawData(d.length - 1)); // draw out of
																										// bounds to
																										// hide stroke
			graph.vertex(dimensions.x + strokeWeight, dimensions.y + strokeWeight); // lower right corner
			graph.vertex(-strokeWeight, dimensions.y + strokeWeight); // lower left corner

			boolean mouseOverStream = false;
			if (withinMoveRegion && pointInPoly(graph, PVector.sub(mousePos, position))) { // mouseOver test
				mouseOverStream = true;
				graph.fill(0xff000000 | ~d.fillColour, 150); // invert rgb, keep alpha
			}

			graph.endShape(PApplet.CLOSE);
			canvas.shape(graph);

			canvas.fill(255);
			canvas.text(d.getDrawData(0), 30, 30);
			canvas.text(d.getRawData(d.pointer), 30, 50); // TODO

			if (mouseOverStream) {
				p.cursor(PApplet.CROSS);
				float x = constrain(mousePos.x, position.x + strokeWeight - 1,
						position.x + dimensions.x - strokeWeight + 1) - position.x; // constrain mouseOverX
				float valAtMouse = d.getDrawData((int) (x / (dimensions.x / (d.length - 1))));
				canvas.stroke(d.fillColour);
				canvas.strokeWeight(max(1, strokeWeight - 1));
				canvas.line(x, dimensions.y, x, dimensions.y - valAtMouse + (strokeWeight - 1)); // line where mouse is
				canvas.text(valAtMouse, 30, 70);
			}
		}
	}

	@Override
	void resize() {
		DataStream d = streams.values().iterator().next(); // TODO: redraw height of existing
		d.setMaxValue(d.maxValue); // TODO
	}

	@Override
	void move() {
		// TODO Auto-generated method stub
	}

	/**
	 * Draws graph background (fill and line segments)
	 * 
	 * @param d
	 */
	private void drawBG(DataStream d) {
		canvas.fill(255, 200); // bg
		canvas.noStroke();
		// canvas.stroke(255, 255, 0); // TODO
		canvas.rect(0, 0, dimensions.x, dimensions.y); // bg

		canvas.stroke(0, 200); // guidelines
		canvas.strokeWeight(1); // guidelines
		float hSegments = 2;
		for (int i = 1; i < hSegments; i++) { // horizontal segments
			canvas.line(0, dimensions.y - i * (dimensions.y / (hSegments + 0)), dimensions.x,
					dimensions.y - i * (dimensions.y / (hSegments + 0)));
		} // calc valuse based on stream max value Y

		float vSegments = 2;
		for (int i = 0; i < vSegments; i++) { // horizontal segments
			float xPos = Math.floorMod(
					(int) ((i * (dimensions.x / vSegments) - (d.pushedCount * dimensions.x / d.length))),
					(int) dimensions.x);
			canvas.line(xPos, 0, xPos, dimensions.y);

		}
	}

	/**
	 * Determine if a point is in a polygon, given by list of vertices.
	 * 
	 * @param s     PShape
	 * @param point PVector point to check
	 * @return boolean
	 */
	private boolean pointInPoly(PShape s, PVector point) {

		boolean c = false;
		int j = s.getVertexCount() - 1;

		for (int i = 0; i < s.getVertexCount(); i++) {
			final PVector v = s.getVertex(i);
			final PVector b = s.getVertex(j);
			if (((v.y > point.y) != (b.y > point.y)) && (point.x < (b.x - v.x) * (point.y - v.y) / (b.y - v.y) + v.x)) {
				c = !c;
			}
			j = i;
		}
		return c;
	}

	/**
	 * Encapsulates Container for data, index, queue position, etc. Supports
	 * smoothing in the form of a moving average. To this end, the length of the
	 * data array must be of size= size+smoothingSize so the first data item we want
	 * to see can be smoothed, rather than skipping the first smoothingSize items.
	 * TODO dynamic ordering / opacity based on mouse-over/highest value TODO max
	 * datapoint/history size (purged thereafter) & viewable datapoints,
	 * 
	 * @author micycle1
	 *
	 */
	private class DataStream implements Comparable<DataStream> {

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
		int pushedCount = 0;

		/**
		 * todo auto push negative so it always scrolls? Scrolls to accomdate new data
		 * only vs will always scroll
		 * 
		 * @param name
		 */
		public DataStream(String name, int history) {
			this.name = name;
			length = history;
			active = true;
			smoothing = 0;
			pointer = 0;
			data = new float[length + smoothing];
			Arrays.fill(data, -1); // init to -1 so not drawn by stroke
			System.out.println(data[19]);
			data[0] = Float.MAX_VALUE;
			fillColour = p.color(50, 50, 130, 150); // TODO
			stroke = p.color(255, 80, 180, 100);
			drawData = new float[length];
			Arrays.fill(drawData, -1); // init to -1 so not drawn by stroke
			System.out.println(drawData[123]);
			// TODO Auto-generated constructor stub
			fill = true;
			outline = true;
		}

		/**
		 * varargs
		 * 
		 * @param datum datapoints
		 */
		public void push(float datum) {

			if (pointer == 0 && this.data[0] == Float.MAX_VALUE) { // on first data point, generate moving average
																	// data,
																	// append to end of array because pointer starts at
																	// 0, so will look
																	// backwards for history
				System.out.println("fill");
				for (int i = 0; i < smoothing; i++) {
					this.data[length + smoothing - i] = datum;
				}
			}

			this.data[pointer] = datum; // push raw datum

			float drawData = datum; // sum of moving average
			for (int i = 0; i < smoothing; i++) { // calc moving average
				int newPointer = Math.floorMod(pointer - 1 - i, length + smoothing); // pointer to previous data, can
																						// wrap around
				drawData += this.data[newPointer]; // sum moving average
			}
			drawData /= (smoothing + 1); // divide to get average

			this.drawData[Math.floorMod(pointer - smoothing - 1, length + smoothing)] = constrain(drawData, 0,
					maxValue - 1) * (dimensions.y / maxValue); // constrain & scale (-1 is stroke Weight)

			pointer++; // inc pointer
			// pointer = ((pointer % length) + smoothing) % (length + smoothing); // recalc
			// pointer (offset to active part of data array)
			pointer %= (length + smoothing);

			// smoothing - pointer
			pushedCount++;
		}

		public void pushEmpty() {
			push(-1); // TODO
		}

		/**
		 * Set new smoothing level
		 * 
		 * @param smoothing
		 */
		public void setSmoothing(int smoothing) {
			smoothing = constrain(smoothing, 0, length); // floor & ceiling
			if (this.smoothing != smoothing) { // perform if different | TODO fix with smoothing data
				float[] tempData = new float[length + smoothing]; // create new buffer
				System.arraycopy(data, this.smoothing, tempData, smoothing, length); // copy into new buffer
				data = tempData; // replace data with new buffer
				this.smoothing = smoothing; // set new smoothing level

				// TODO recalc drawData
			}
		}

		public void setMaxValue(float maxValue) {
			this.maxValue = maxValue;
			// TODO recalc drawvalues w/ smoothing
			for (int i = 0; i < drawData.length; i++) {
				drawData[i] = constrain(data[i], 0, maxValue - 1) * (dimensions.y / maxValue);
			}
		}

		public void setColor(int color) {
			this.fillColour = color;
		}

		public void setActive(boolean active) {
			this.active = active;
		}

		/**
		 * Get draw data that is logically at the index given (where 0 is left most
		 * datapoint) or, ordered by recency, where 0 is oldest data point
		 * 
		 * @param index
		 * @return
		 */
		public float getDrawData(int index) {
			int i = Math.floorMod(pointer + index - 1, length + smoothing); // -1, because pointer is incremented after
																			// push
			return drawData[i];
		}

		/**
		 * Get draw data that is logically at the index given (where 0 is left most
		 * datapoint)
		 * 
		 * @param index
		 * @return
		 */
		public float getRawData(int index) { // TODO
			int i = Math.floorMod((pointer + index - 1) - (length), length + smoothing); // -1, because pointer is
																							// incremented after push
			return data[index];
		}

		/**
		 * Used to determine iteration order
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

}
