package pathing;

import java.util.ArrayList;
import java.util.HashMap;

import processing.core.PApplet;
import processing.core.PGraphics;
import processing.core.PVector;
import processing.event.MouseEvent;

/**
 * Add, push then draw
 * Each graph can contain multiple datastreams.
 * TODO change settings (mouseover etc) + drag + pause + mouseover value + toggle stream visibility+order
 * @author micycle1
 *
 */
public class ScrollMonitor {

	private final PGraphics g;
	private PApplet p;
	private final PVector dimensions, position;
	private final ArrayList<float[]> data;
	/**
	 * will be the same if all datastreams have same buffer size
	 */
	private final ArrayList<Integer> pointers;
	private final HashMap<String, Integer> streamNamesIndex;
	private final HashMap<String, DataStream> streams; // TODO
	private final HashMap<Integer, Integer> streamColours;
	private final ArrayList<Integer> streamSmoothing; // TODO change to hashmap if allow out-of-order stream iteration
	private float max;
	private boolean fill = true;

	// private SortedMap<DataStream, Integer> todo; // TODO, ordered iteration

	private PVector mouseDownPos, cachePos;
	private boolean dragging = false;

	/**
	 * Create graph, define using PVectors
	 * @param g parent papplet
	 * @param position top left corner
	 * @param dimensions size of scroll graph
	 * @param max  leave empty for auto scale?
	 */
	// public ScrollMonitor(PGraphics g, PVector position, PVector dimensions, float max) {
	// this.g = g;
	// this.position = position;
	// this.dimensions = dimensions;
	// data = new ArrayList<>();
	// pointers = new ArrayList<>();
	// streamNamesIndex = new HashMap<>();
	// streamColours = new HashMap<>();
	// streamSmoothing = new ArrayList<>();
	// this.max = max;
	// }
	//
	// /**
	// * Create graph, define using int
	// * @param g
	// * @param positionX
	// * @param positionY
	// * @param width
	// * @param height
	// * @param max
	// */
	// public ScrollMonitor(PGraphics g, int positionX, int positionY, int width, int height, float max) {
	// this.g = g;
	// this.position = new PVector(positionX, positionY);
	// this.dimensions = new PVector(width, height);
	// data = new ArrayList<>();
	// pointers = new ArrayList<>();
	// streamNamesIndex = new HashMap<>();
	// streamColours = new HashMap<>();
	// streamSmoothing = new ArrayList<>();
	// this.max = max;
	// }

	/**
	 * 
	 * @param g parent papplet
	 * @param position top left corner
	 * @param dimensions size of scroll graph
	 * @param max  leave empty for auto scale?
	 */
	public ScrollMonitor(PApplet p, PVector position, PVector dimensions, float max) {
		this.p = p;
		this.g = p.getGraphics();
		this.position = position;
		this.dimensions = dimensions;
		data = new ArrayList<>();
		pointers = new ArrayList<>();
		streamNamesIndex = new HashMap<>();
		streamColours = new HashMap<>();
		streamSmoothing = new ArrayList<>();
		streams = new HashMap<>();
		this.max = max;
		p.registerMethod("mouseEvent", this);
	}

	/**
	 * strng name also / ID
	 * @param historyCap
	 */
	public void addDataStream(String name, int historyCap) {
		// float[] data = new float[historyCap];
		// Arrays.fill(data, -1); // init to -1 so not drawn by stroke
		// this.data.add(data);
		// pointers.add(0);
		// streamSmoothing.add(1); // default smoothing: (1=none)

		streams.put(name, new DataStream(name, historyCap));
	}

	// /**
	// * strng name also / ID
	// * @param historyCap
	// */
	// public void addDataStream(int historyCap, String name, int color) {
	// float[] data = new float[historyCap];
	// Arrays.fill(data, -1);
	// this.data.add(data);
	// pointers.add(0);
	// streamNamesIndex.put(name, this.data.size() - 1);
	// streamColours.put(this.data.size() - 1, color);
	// streamSmoothing.add(1); // default smoothing: (1=none)
	// }

	// /**
	// * Push new data to a stream identified by its index.
	// * @param dataStream
	// * @param n
	// */
	// public void push(int dataStream, float n) {
	// data.get(dataStream)[pointers.get(dataStream)] = n;
	// pointers.set(dataStream, (pointers.get(dataStream) + 1) % data.get(dataStream).length);
	// }

	/**
	 * Push new data to a stream identified by its name.
	 * @param dataStreamName
	 * @param n
	 */
	public void push(String dataStreamName, float n) {
		// int dataStream = streamNamesIndex.get(dataStreamName);
		// data.get(dataStream)[pointers.get(dataStream)] = n;
		// pointers.set(dataStream, (pointers.get(dataStream) + 1) % data.get(dataStream).length);
		streams.get(dataStreamName).push(n);
	}

	/**
	 * Set the level of visual smoothing a stream has using a equal-weighted moving average of the last n terms
	 * TODO INCREASE STREAM SIZE BY SMOOTHING AMOUNT
	 * @param dataStreamName
	 * @param smoothing default = 1 == no smoothing; must be >=1
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

	private boolean mouseOverPoly(float[] vertx, float[] verty, PVector point) {

		boolean c = false;
		int j = vertx.length - 1;
		float testx = point.x;
		float testy = point.y;
		for (int i = 0; i < vertx.length; i++) {
			if (((verty[i] > testy) != (verty[j] > testy))
					&& (testx < (vertx[j] - vertx[i]) * (testy - verty[i]) / (verty[j] - verty[i]) + vertx[i])) {
				c = !c;
			}
			j = i;
		}
		return c;
	}

	/**
	 * draw a specifed datastream
	 * @param dataStream
	 */
	public void draw(String dataStream) {

	}

	public void draw2() {

		PVector mousePos = new PVector(p.mouseX, p.mouseY);
		boolean mouseOverMonitor = withinRegion(mousePos, position, PVector.add(position, dimensions));

		if (!dragging) {
			if (mouseOverMonitor) {
				p.cursor(PApplet.HAND);
			} else {
				p.cursor(PApplet.ARROW);
			}
		} else {
			position.set(PVector.sub(mousePos, mouseDownPos).add(cachePos)); // dragging
		}

		drawBG();

		g.fill(125, 200, 100, 170);
		g.strokeWeight(3);
		g.stroke(255, 0, 0, 200);
		// g.noStroke();
		for (DataStream d : streams.values()) {

			g.beginShape();
			int datumIndex = 0;

			for (int i = 0; i < d.length; i++) {
				float val = d.getDrawData(i);
				float x = position.x + datumIndex * (dimensions.x / d.length); // calc x coord -- scale to x dimension
				float y = (position.y + dimensions.y) - PApplet.ceil(val);
				g.vertex(x, y);
				datumIndex++;
			}
			g.vertex(position.x + dimensions.x, position.y + dimensions.y); // lower right corner
			g.vertex(position.x, position.y + dimensions.y); // lower left corner
			g.endShape(PApplet.CLOSE);

		}
	}

	private void drawBG() {
		g.fill(255, 200); // bg
		g.noStroke();
		// g.stroke(255, 255, 0); // TODO
		g.rect(position.x, position.y, dimensions.x, dimensions.y); // bg

		g.stroke(0, 200); // guidelines
		g.strokeWeight(1); // guidelines
		float segments = 4;
		for (int i = 1; i < segments; i++) {
			g.line(position.x, position.y + dimensions.y - i * (dimensions.y / (segments + 0)),
					position.x + dimensions.x, position.y + dimensions.y - i * (dimensions.y / (segments + 0)));
		}
	}

	public void draw() {

		PVector mousePos = new PVector(p.mouseX, p.mouseY);
		boolean mouseOverMonitor = withinRegion(mousePos, position, PVector.add(position, dimensions));

		if (!dragging) {
			if (mouseOverMonitor) {
				p.cursor(PApplet.HAND);
			} else {
				p.cursor(PApplet.ARROW);
			}
		} else {
			position.set(PVector.sub(mousePos, mouseDownPos).add(cachePos)); // dragging
		}

		g.fill(255, 200); // bg
		g.noStroke();
		// g.stroke(255, 255, 0); // TODO
		g.rect(position.x, position.y, dimensions.x, dimensions.y); // bg

		g.stroke(0, 200); // guidelines
		g.strokeWeight(1); // guidelines
		float segments = 4;
		for (int i = 1; i < segments; i++) {
			g.line(position.x, position.y + dimensions.y - i * (dimensions.y / (segments + 0)),
					position.x + dimensions.x, position.y + dimensions.y - i * (dimensions.y / (segments + 0)));
		}

		g.strokeWeight(3);
		int dataIndex = 0; // datastream index
		g.noFill(); // nofill here
		for (float[] stream : data) {
			g.stroke(streamColours.get(dataIndex));
			if (fill) {
				g.fill(streamColours.get(dataIndex), 100);
			}
			g.beginShape();


			float[] vertx = new float[stream.length + 2];
			float[] verty = new float[stream.length + 2];

			float pVertX = position.x;
			float pVertY = position.y;
			float tempMax = 0;
			for (int i = 0; i < stream.length + 1; i++) { // plot datapoint vertices
				float x = position.x + i * (dimensions.x / stream.length); // calc x coord -- scale to x dimension

				float val;
				if (i >= streamSmoothing.get(dataIndex)) { // dont smooth first N datapoints
					float sum = 0; // sum over n last datapoints (rolling average)
					for (int j = 0; j < streamSmoothing.get(dataIndex); j++) {
						// sum+= stream[ (i + pointers.get(index-smoothing) -j) % stream.length]; // raw val
						int in = Math.floorMod((i + pointers.get(dataIndex) - j - 1), stream.length);
						// System.out.println(in);
						sum += stream[in];
					}
					sum /= streamSmoothing.get(dataIndex);
					val = PApplet.min(sum, max) * (dimensions.y / max); // constrain & scale
				} else {
					val = stream[(i + pointers.get(dataIndex)) % stream.length]; // raw val
				}
				tempMax = PApplet.max(max, val);
				max = tempMax;

				float y = (position.y + dimensions.y) - PApplet.ceil(val);
				vertx[i] = x; // push vertex to array
				verty[i] = y; // push vertex to array
				g.vertex(x, PApplet.max(0, y)); // don't draw negative vals

				if (val >= 0) { // will ignore 0 values
					g.line(pVertX, pVertY, x, PApplet.min(y, position.y + dimensions.y - 2)); // min of 2, to account for strokeWidth
				}

				pVertX = x;
				pVertY = PApplet.min(y, position.y + dimensions.y - 2);
			}
			if (fill) {
				g.vertex(position.x + dimensions.x, position.y + dimensions.y); // lower right corner
				vertx[stream.length] = position.x + dimensions.x;
				verty[stream.length] = position.y + dimensions.y;

				g.vertex(position.x, position.y + dimensions.y); // lower left corner
				vertx[stream.length + 1] = position.x;
				verty[stream.length + 1] = position.y + dimensions.y;

				if (mouseOverMonitor && mouseOverPoly(vertx, verty, mousePos)) {
					g.fill(streamColours.get(dataIndex), 145);
				}
				g.noStroke();
				g.endShape(PApplet.CLOSE);
			} else {
				g.endShape();
			}
			dataIndex++;
		}
	}

	/**
	 * This method is <b>public</b> only to enable binding to a parent PApplet.
	 * <p>You can <b>ignore this method</b> since the parent sketch will call it automatically
	 * when it detects a mouse event (provided register() has been called).
	 */
	public final void mouseEvent(MouseEvent e) {
		switch (e.getAction()) {
			case processing.event.MouseEvent.PRESS :
				mousePressed(e);
				break;
			case processing.event.MouseEvent.RELEASE :
				mouseReleased(e);
				break;
			case processing.event.MouseEvent.CLICK :
				mouseClicked(e);
				break;
			case processing.event.MouseEvent.WHEEL :
				mouseWheel(e);
				break;
			case processing.event.MouseEvent.DRAG :
				mouseDragged(e);
				break;
			default :
				break;
		}
	}

	/**
	 * Called automatically when the parent PApplet issues a <b>PRESS</b> {@link processing.event.MouseEvent MouseEvent}.
	 * <p>Therefore write any code here that should be executed when the mouse is <b>pressed</b>.
	 */
	private void mousePressed(MouseEvent e) {
		mouseDownPos = new PVector(p.mouseX, p.mouseY);

		if (e.getButton() == PApplet.LEFT && withinRegion(mouseDownPos, position, PVector.add(position, dimensions))) {
			System.out.println("over");
			p.cursor(PApplet.MOVE); // ARROW, CROSS, HAND, MOVE, TEXT, or WAIT
			cachePos = position.copy();
			dragging = true;
		}
	}

	/**
	 * Called automatically when the parent PApplet issues a <b>RELEASE</b> {@link processing.event.MouseEvent MouseEvent}.
	 * <p>Therefore write any code here that should be executed when the mouse is <b>released</b>.
	 */
	private void mouseReleased(MouseEvent e) {
		if (dragging) {
			dragging = false;
		}
	}

	/**
	 * Called automatically when the parent PApplet issues a <b>CLICK</b> {@link processing.event.MouseEvent MouseEvent}.
	 * <p>Therefore write any code here that should be executed when the mouse is <b>clicked</b>. 
	 * (a press and release in quick succession).
	 */
	private void mouseClicked(MouseEvent e) {
	}

	/**
	 * Called automatically when the parent PApplet issues a <b>WHEEL</b> {@link processing.event.MouseEvent MouseEvent}.
	 * <p>Therefore write any code here that should be executed when the mouse wheel is <b>scrolled</b>.
	 * <p> Use the getCount() method of the MouseEvent e parameter to get the scroll direction.
	 */
	private void mouseWheel(MouseEvent e) {
	}

	/**
	 * Called automatically when the parent PApplet issues a <b>DRAG</b> {@link processing.event.MouseEvent MouseEvent}.
	 * <p>Therefore write any code here that should be executed when the mouse is <b>dragged</b>.
	 */
	private void mouseDragged(MouseEvent e) {
	}

	/**
	 * Determine if a point is within a rectangular region -- PVector params.
	 * @param point PVector position to test.
	 * @param UL Corner one of region.
	 * @param BR Corner two of region (different X & Y).
	 * @return True if point contained in region.
	 */
	private static boolean withinRegion(PVector point, PVector UL, PVector BR) {
		return (point.x >= UL.x && point.y >= UL.y) && (point.x <= BR.x && point.y <= BR.y) // SE
				|| (point.x >= BR.x && point.y >= BR.y) && (point.x <= UL.x && point.y <= UL.y) // NW
				|| (point.x <= UL.x && point.x >= BR.x) && (point.y >= UL.y && point.y <= BR.y) // SW
				|| (point.x <= BR.x && point.x >= UL.x) && (point.y >= BR.y && point.y <= UL.y); // NE
	}

	/**
	 * Container for data, index, queue position, etc.
	 * Supports smoothing in the form of a moving average. To this end, the length of the 
	 * data array must be of size= size+smoothingSize so the first data item we want 
	 * to see can be smoothed, rather than skipping the first smoothingSize items. 
	 * TODO dynamic ordering / opacity based on mouse-over/highest value
	 * @author micycle1
	 *
	 */
	class DataStream implements Comparable<DataStream> {

		/**
		 * Position in queue
		 */
		int queueIndex;
		/**
		 * DataStream name (identifier)
		 */
		final String name;
		/**
		 * Which index in {@link #data} is the most recent, and where iteration should start
		 */
		int pointer;
		/**
		 * Fill color when drawn
		 */
		int color;
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
		 * Ceiling value of data drawn (doesn't affect data pushed)
		 */
		float maxValue;
		/**
		 * Buffer/history size (size of {@link #data})
		 */
		final int length;
		int direction = 1; // 1 scroll to left; -1 scroll to right

		/**
		 * todo auto push negative so it always scrolls?
		 * Scrolls to accomdate new data only vs will always scroll 
		 * @param name
		 */
		public DataStream(String name, int history) {
			this.name = name;
			length = history;
			active = true;
			smoothing = 0;
			pointer = 0;
			data = new float[length + smoothing];
			color = 0 - (255 << 8);
			drawData = new float[length];
			// TODO Auto-generated constructor stub
		}

		/**
		 * varargs
		 * @param datum datapoints
		 */
		public void push(float datum) {

			if (pointer == 0 && this.data[0] == Float.MAX_VALUE) { // fill last values with moving average data
				for (int i = 0; i < smoothing; i++) {
					this.data[length + smoothing - i] = datum;
				}
			}

			this.data[pointer] = datum; // push raw datum

			float drawData = datum; // sum of moving average
			for (int i = 0; i < smoothing; i++) { // calc moving average
				int newPointer = Math.floorMod(pointer - 1 - i, length + smoothing); // pointer to previous data, can wrap around
				drawData += this.data[newPointer]; // sum moving average
			}
			drawData /= (smoothing + 1); // divide to get average
			 
			this.drawData[Math.floorMod(pointer-smoothing-1, length + smoothing)] = PApplet.constrain(drawData, 0, maxValue - 1)
					* (dimensions.y / maxValue); // constrain & scale (-1 is stroke Weight)

			pointer++; // inc pointer
			// pointer = ((pointer % length) + smoothing) % (length + smoothing); // recalc pointer (offset to active part of data array)
			pointer %= (length + smoothing);

			// smoothing - pointer
		}

		public void pushEmpty() {
			push(Float.MAX_VALUE); // TODO
		}

		/**
		 * Set new smoothing level
		 * @param smoothing
		 */
		public void setSmoothing(int smoothing) {
			smoothing = PApplet.constrain(smoothing, 0, length); // floor & ceiling
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
				drawData[i] = PApplet.constrain(data[i], 0, maxValue - 1) * (dimensions.y / maxValue);
			}
		}

		public void setColor(int color) {
			this.color = color;
		}

		public void setActive(boolean active) {
			this.active = active;
		}

		/**
		 * Get draw data that is logically at the index given (0 is left most datapoint)
		 * @param index
		 * @return
		 */
		public float getDrawData(int index) {
			return drawData[(pointer + index) % (length + smoothing)];
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
