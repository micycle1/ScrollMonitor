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
	private HashMap<String, DataStream> streams; // TODO
	private final HashMap<Integer, Integer> streamColours;
	private final ArrayList<Integer> streamSmoothing; // TODO change to hashmap if allow out-of-order stream iteration
	private final float max;
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
	public ScrollMonitor(PGraphics g, PVector position, PVector dimensions, float max) {
		this.g = g;
		this.position = position;
		this.dimensions = dimensions;
		data = new ArrayList<>();
		pointers = new ArrayList<>();
		streamNamesIndex = new HashMap<>();
		streamColours = new HashMap<>();
		streamSmoothing = new ArrayList<>();
		this.max = max;
	}

	/**
	 * Create graph, define using int 
	 * @param g
	 * @param positionX
	 * @param positionY
	 * @param width
	 * @param height
	 * @param max
	 */
	public ScrollMonitor(PGraphics g, int positionX, int positionY, int width, int height, float max) {
		this.g = g;
		this.position = new PVector(positionX, positionY);
		this.dimensions = new PVector(width, height);
		data = new ArrayList<>();
		pointers = new ArrayList<>();
		streamNamesIndex = new HashMap<>();
		streamColours = new HashMap<>();
		streamSmoothing = new ArrayList<>();
		this.max = max;
	}

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
		this.max = max;
		p.registerMethod("mouseEvent", this);
	}

	/**
	 * strng name also / ID
	 * @param historyCap
	 */
	public void addDataStream(int historyCap) {
		data.add(new float[historyCap]);
		pointers.add(0);
		streamSmoothing.add(1); // default smoothing: (1=none)
	}

	/**
	 * strng name also / ID
	 * @param historyCap
	 */
	public void addDataStream(int historyCap, String name, int color) {
		data.add(new float[historyCap]);
		pointers.add(0);
		streamNamesIndex.put(name, data.size() - 1);
		streamColours.put(data.size() - 1, color);
		streamSmoothing.add(1); // default smoothing: (1=none)
	}

	/**
	 * Push new data to a stream identified by its index.
	 * @param dataStream
	 * @param n
	 */
	public void push(int dataStream, float n) {
		data.get(dataStream)[pointers.get(dataStream)] = n;
		pointers.set(dataStream, (pointers.get(dataStream) + 1) % data.get(dataStream).length);
	}

	/**
	 * Push new data to a stream identified by its name.
	 * @param dataStreamName
	 * @param n
	 */
	public void push(String dataStreamName, float n) {
		int dataStream = streamNamesIndex.get(dataStreamName);
		data.get(dataStream)[pointers.get(dataStream)] = n;
		pointers.set(dataStream, (pointers.get(dataStream) + 1) % data.get(dataStream).length);
	}

	/**
	 * Set the level of visual smoothing a stream has using a equal-weighted moving average of the last n terms
	 * TODO INCREASE STREAM SIZE BY SMOOTHING AMOUNT
	 * @param dataStreamName
	 * @param smoothing default = 1 == no smoothing; must be >=1
	 */
	public void setSmoothing(String dataStreamName, int smoothing) {
		int dataStream = streamNamesIndex.get(dataStreamName);
		streamSmoothing.set(dataStream, smoothing);
	}

	public void draw() {

		PVector mousePos = new PVector(p.mouseX, p.mouseY);

		if (!dragging) {
			if (withinRegion(mousePos, position, PVector.add(position, dimensions))) {
				p.cursor(PApplet.HAND);
			} else {
				p.cursor(PApplet.ARROW);
			}
		} else {
			position.set(PVector.sub(mousePos, mouseDownPos).add(cachePos)); // dragging
		}

		g.fill(255, 150); // bg
		g.noStroke();
		g.rect(position.x, position.y, dimensions.x, dimensions.y); // bg

		g.stroke(255, 150); // guidelines
		g.strokeWeight(1); // guidelines
		float segments = 4;
		for (int i = 1; i < segments; i++) {
			g.line(position.x, position.y + dimensions.y - i * (dimensions.y / (segments + 0)),
					position.x + dimensions.x, position.y + dimensions.y - i * (dimensions.y / (segments + 0)));
		}

		g.strokeWeight(2);
		int dataIndex = 0; // datastream index
		g.noFill(); // nofill here
		for (float[] stream : data) {
			g.stroke(streamColours.get(dataIndex), 255);
			if (fill) {
				g.fill(streamColours.get(dataIndex), 100);
			}
			g.beginShape();

			// for (int i = 0; i < streamSmoothing.get(dataIndex); i++) { // dont smooth first values
			// float x = position.x + i * (dimensions.x / stream.length); // calc x coord -- scale to x dimension
			// int in = (i + pointers.get(dataIndex)-1) % stream.length;
			// float val = PApplet.constrain(stream[in], 0, max) * (dimensions.y / max);
			// float y = (position.y + dimensions.y) - val;
			// g.vertex(x, y);
			// }

			// for (int i = streamSmoothing.get(dataIndex); i < stream.length + 1; i++) {
			//
			// }

			// TODO ignore smoothing for first 'streamSmoothing.get(dataIndex)' values

			// TODO BUFFER NEEDS TO BE OF SIZE: stream.length+streamSmoothing.get(dataIndex)

			for (int i = 0; i < stream.length + 1; i++) { // plot datapoint vertices
				float x = position.x + i * (dimensions.x / stream.length); // calc x coord -- scale to x dimension

				float val;
				if (i > streamSmoothing.get(dataIndex)) { // dont smooth first N datapoints
					float sum = 0; // sum over n last datapoints (rolling average)
					for (int j = 0; j < streamSmoothing.get(dataIndex); j++) {
						// sum+= stream[ (i + pointers.get(index-smoothing) -j) % stream.length]; // raw val
						int in = Math.floorMod((i + pointers.get(dataIndex) - j - 1), stream.length);
						// System.out.println(in);
						sum += stream[in];
					}
					sum /= streamSmoothing.get(dataIndex);
					val = PApplet.constrain(sum, 0, max) * (dimensions.y / max); // constrain & scale
				} else {
					val = stream[(i + pointers.get(dataIndex)) % stream.length]; // raw val
				}

				float y = (position.y + dimensions.y) - val;
				g.vertex(x, y);
			}
			if (fill) {
				g.vertex(position.x + dimensions.x, position.y + dimensions.y); // lower right corner
				g.vertex(position.x, position.y + dimensions.y); // lower left corner
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

		if (withinRegion(mouseDownPos, position, PVector.add(position, dimensions))) {
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
		 * Data
		 */
		float[] data;
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
		int length;

		public DataStream(String name) {
			this.name = name;
			active = true;
			smoothing = 0;
			pointer = smoothing; // start at smoothing _____[]......
			data = new float[length + smoothing];
			// TODO Auto-generated constructor stub
		}

		public void push(float n) {
			data[pointer] = n;
			pointer++;
		}

		/**
		 * Set new smoothing level
		 * @param smoothing
		 */
		public void setSmoothing(int smoothing) {
			smoothing = PApplet.constrain(smoothing, 0, length); // floor & ceiling
			if (this.smoothing != smoothing) { // perform if different
				float[] tempData = new float[length + smoothing]; // create new buffer
				System.arraycopy(data, this.smoothing, tempData, smoothing, length); // copy into new buffer

				data = tempData; // replace data with new buffer
				this.smoothing = smoothing; // set new smoothing level
			}
		}

		public void setColor(int color) {
			this.color = color;
		}

		public void setActive(boolean active) {
			this.active = active;
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
