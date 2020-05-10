package pathing;

import static processing.core.PApplet.constrain;

import java.util.Arrays;
import java.util.HashMap;

import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import processing.core.PApplet;
import processing.core.PGraphics;
import processing.core.PShape;
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

	private PGraphics g;
	private final PApplet p;
	private final PVector dimensions, position;

	private final HashMap<String, DataStream> streams;
	// private SortedMap<DataStream, Integer> todo; // TODO, ordered iteration

	private PVector mousePos, mouseDownPos;
	private PVector cachePos, cacheDimensions;
	/**
	 * drag border buffer
	 */
	private final PVector buffer;
	private boolean dragging = false, resizing = false;

	private enum SIDES {
		LEFT, RIGHT, TOP, BOTTOM
	};
	private SIDES resizeSide;

	private boolean[] resizeSides = new boolean[4]; // L,U,D,R
	private boolean lockPosition, lockDimensions; // TODO
	private boolean mouseOverDatastream = false;

	private enum RENDERERS {
		JAVA2D, JAVAFX, P2D
	} // todo find renderer, then use to set horizontal/vert cursor
	private final RENDERERS renderer;

	private Scene pAppletFXscene = null;

	/**
	 * 
	 * @param g parent papplet
	 * @param position top left corner
	 * @param dimensions size of scroll graph
	 * @param max  leave empty for auto scale?
	 */
	public ScrollMonitor(PApplet p, PVector position, PVector dimensions) {
		this.p = p;
		System.out.println(p.sketchRenderer());
		g = p.createGraphics((int) dimensions.x, (int) dimensions.y);
		this.position = position;
		this.dimensions = dimensions;
		buffer = new PVector(20, 20);

		streams = new HashMap<>();
		p.registerMethod("mouseEvent", this);

		switch (p.sketchRenderer()) {
			case "processing.javafx.PGraphicsFX2D" :
				renderer = RENDERERS.JAVAFX;
				pAppletFXscene = ((Canvas) p.getSurface().getNative()).getScene();
				break;
			case "processing.awt.PGraphicsJava2D" :
				renderer = RENDERERS.JAVA2D;
				break;

			default :
				renderer = RENDERERS.JAVA2D; // or null?
				break;
		}
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

	public void setStreamColour(String dataStream, int color) {
		streams.get(dataStream).setColor(color);
	}

	private boolean mouseOverPoly(PShape s, PVector point) {

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
	 * draw a specifed datastream
	 * @param dataStream
	 */
	public void draw(String dataStream) {
		// TODO
	}

	public void post() {
		// todo register this instead?
	}

	public void draw() {

		mousePos = new PVector(PApplet.max(p.mouseX, 0), PApplet.max(p.mouseY, 0));
		boolean mouseOverMonitor = withinRegion(mousePos, position, PVector.add(position, dimensions)); // mouseover rect region?
		boolean withinMoveRegion = withinRegion(mousePos, PVector.add(position, buffer),
				PVector.add(position, dimensions).sub(buffer));

		if (!dragging) { // dragging = false;
			if (resizing) {
				resize(); // todo recalc pos & dimensions
			} else {
				if (withinMoveRegion) { // Cursor: Move Monitor
					p.cursor(PApplet.HAND);
				} else if (mouseOverMonitor) { // Cursor: Resize Monitor
					calcSidesMouseOver();
					switch (renderer) {
						case JAVAFX :
							// todo calc dir here
							cursorFX();
							break;
						default :
							p.cursor(PApplet.CROSS); // ARROW, CROSS, HAND, MOVE, TEXT, or WAIT
							break;
					}
				} else { // default
					p.cursor(PApplet.ARROW);
				}
			}

		} else { // dragging = true
			position.set(PVector.sub(mousePos, mouseDownPos).add(cachePos)); // dragging
		}

		g.beginDraw(); // move after resize calc
		g.strokeCap(PApplet.ROUND);
		g.clear();

		drawBG(streams.values().iterator().next()); // TODO

		int strokeWeight = 3;
		g.strokeWeight(strokeWeight);

		for (DataStream d : streams.values()) { // TODO draw back to front
			g.stroke(d.stroke);
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

			graph.vertex(dimensions.x + strokeWeight, (dimensions.y) - d.getDrawData(d.length - 1)); // draw out of bounds to hide stroke
			graph.vertex(dimensions.x + strokeWeight, dimensions.y + strokeWeight); // lower right corner
			graph.vertex(-strokeWeight, dimensions.y + strokeWeight); // lower left corner

			boolean mouseOverStream = false;
			if (withinMoveRegion && mouseOverPoly(graph, PVector.sub(mousePos, position))) { // mouseOver test
				mouseOverStream = true;
				graph.fill(0xff000000 | ~d.fillColour, 150); // invert rgb, keep alpha
			}

			graph.endShape(PApplet.CLOSE);
			g.shape(graph);

			g.fill(255);
			g.text(d.getDrawData(0), 30, 30);
			g.text(d.getRawData(d.pointer), 30, 50); // TODO

			if (mouseOverStream) {
				p.cursor(PApplet.CROSS);
				float x = constrain(mousePos.x, position.x + strokeWeight - 1,
						position.x + dimensions.x - strokeWeight + 1) - position.x; // constrain mouseOverX
				float valAtMouse = d.getDrawData((int) (x / (dimensions.x / (d.length - 1))));
				g.stroke(d.fillColour);
				g.strokeWeight(PApplet.max(1, strokeWeight - 1));
				g.line(x, dimensions.y, x, dimensions.y - valAtMouse + (strokeWeight - 1)); // line where mouse is
				g.text(valAtMouse, 30, 70);
			}
		}
		g.endDraw();
		p.image(g, position.x, position.y);
	}

	private void calcSidesMouseOver() {
		resizeSides[0] = resizeSides[0] = withinRegion(mousePos, position,
				PVector.add(position, new PVector(buffer.x, dimensions.y - buffer.x))); // left side
		resizeSides[1] = withinRegion(mousePos, position,
				PVector.add(position, new PVector(dimensions.x + buffer.x, buffer.y))); // top side
		resizeSides[2] = withinRegion(mousePos, new PVector(0, dimensions.y).add(position),
				new PVector(dimensions.x, dimensions.y).add(position).sub(buffer)); // bottom side
		resizeSides[3] = withinRegion(mousePos, new PVector(dimensions.x, 0).add(position),
				new PVector(dimensions.x - buffer.x, dimensions.y - buffer.y).add(position)); // right side
	}

	/**
	 * Set edge cursor in JavaFX mode 
	 */
	private void cursorFX() {
		// L,U,D,R
		if (resizeSides[0]) { // LEFT SIDE
			if (resizeSides[1]) { // ↖
				pAppletFXscene.setCursor(Cursor.NW_RESIZE);
			} else if (resizeSides[2]) { // ↙
				pAppletFXscene.setCursor(Cursor.SW_RESIZE);
			} else { // ←
				pAppletFXscene.setCursor(Cursor.W_RESIZE);
			}
		} else if (resizeSides[1]) { // TOP SIDE
			if (resizeSides[3]) { // ↗
				pAppletFXscene.setCursor(Cursor.NE_RESIZE);
			} else { // ↑
				pAppletFXscene.setCursor(Cursor.N_RESIZE);
			}
		} else if (resizeSides[2]) { // BOTTOM SIDE
			if (resizeSides[3]) { // ↘
				pAppletFXscene.setCursor(Cursor.SE_RESIZE);
			} else { // ↓
				pAppletFXscene.setCursor(Cursor.S_RESIZE);
			}
		} else { // →
			pAppletFXscene.setCursor(Cursor.E_RESIZE);
		}
	}

	private void resize() {
		PVector minSize = new PVector(25, 10);
		PVector posBound = PVector.add(cachePos, cacheDimensions).sub(minSize); // stops moving the monitor when resizing past prior edge
		PVector newDims = PVector.add(cacheDimensions, cachePos).sub(mousePos);
		newDims.set(PApplet.max(newDims.x, minSize.x), PApplet.max(newDims.y, minSize.y));
		
		if (resizeSides[0]) { // LEFT SIDE
			if (resizeSides[1]) { // ↖
				position.set(PApplet.min(mousePos.x, posBound.x), PApplet.min(mousePos.y, posBound.y));
				dimensions.set(newDims.x, newDims.y);
			} else if (resizeSides[2]) { // ↙
				position.set(PApplet.min(mousePos.x, posBound.x), PApplet.min(mousePos.y, posBound.y));
				dimensions.set(newDims.x, newDims.y);
			} else { // ←
				position.set(PApplet.min(mousePos.x, posBound.x), position.y);
				dimensions.set(newDims.x, dimensions.y);
			}
		} else if (resizeSides[1]) { // TOP SIDE
			if (resizeSides[3]) { // ↗
				position.set(position.x, PApplet.min(mousePos.y, posBound.y));
				dimensions.set(newDims.x, newDims.y);
			} else { // ↑
				position.set(position.x, PApplet.min(mousePos.y, posBound.y));
				dimensions.set(dimensions.x, newDims.y);
			}
		} else if (resizeSides[2]) { // BOTTOM SIDE
			if (resizeSides[3]) { // ↘

			} else { // ↓

			}
		} else { // →

		}
		g = p.createGraphics((int) dimensions.x, (int) dimensions.y);
		DataStream d= streams.values().iterator().next(); // TODO: redraw height of existing
		d.setMaxValue(d.maxValue); // TODO
	}

	public void hide() {
		// TODO
	}

	public void show() {
		// TODO
	}

	public void hideStream(String dataStream) {
		// TODO
	}

	/**
	 * Enable visibility for a datastream
	 * @param dataStream
	 */
	public void showStream(String dataStream) {
		// TODO
	}

	private void drawBG(DataStream d) {
		g.fill(255, 200); // bg
		g.noStroke();
		// g.stroke(255, 255, 0); // TODO
		g.rect(0, 0, dimensions.x, dimensions.y); // bg

		g.stroke(0, 200); // guidelines
		g.strokeWeight(1); // guidelines
		float hSegments = 2;
		for (int i = 1; i < hSegments; i++) { // horizontal segments
			g.line(0, dimensions.y - i * (dimensions.y / (hSegments + 0)), dimensions.x,
					dimensions.y - i * (dimensions.y / (hSegments + 0)));
		} // calc valuse based on stream max value Y

		float vSegments = 2;
		for (int i = 0; i < vSegments; i++) { // horizontal segments
			float xPos = Math.floorMod(
					(int) ((i * (dimensions.x / vSegments) - (d.pushedCount * dimensions.x / d.length))),
					(int) dimensions.x);
			g.line(xPos, 0, xPos, dimensions.y);

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

		if (e.getButton() == PApplet.LEFT && !mouseOverDatastream) {
			boolean withinTotalRegion = withinRegion(mouseDownPos, position, PVector.add(position, dimensions));
			if (withinTotalRegion) {
				boolean withinMoveRegion = withinRegion(mouseDownPos, PVector.add(position, buffer),
						PVector.add(position, dimensions).sub(buffer));
				cachePos = position.copy();
				if (withinMoveRegion) { // move
					p.cursor(PApplet.MOVE); // ARROW, CROSS, HAND, MOVE, TEXT, or WAIT
					dragging = true;
				} else { // resize // L,U,D,R
					resizing = true;
					cacheDimensions = dimensions.copy();
					System.out.println(Arrays.toString(resizeSides));
				}
			}
		}
	}

	/**
	 * Called automatically when the parent PApplet issues a <b>RELEASE</b> {@link processing.event.MouseEvent MouseEvent}.
	 * <p>Therefore write any code here that should be executed when the mouse is <b>released</b>.
	 */
	private void mouseReleased(MouseEvent e) {
		dragging = false;
		resizing = false;
		resizeSides = new boolean[4];
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
		// streams.values().iterator().next().length+= e.getAction()*50;
	}

	/**
	 * Called automatically when the parent PApplet issues a <b>DRAG</b> {@link processing.event.MouseEvent MouseEvent}.
	 * <p>Therefore write any code here that should be executed when the mouse is <b>dragged</b>.
	 */
	private void mouseDragged(MouseEvent e) {
		if (resizing) {
			// resize pgraphics here
		}
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

	private int darkenColor(int c, float percentage) { // TODO
		percentage = constrain(percentage, 0, 1);
		int r = (c >> 16) & 0xff;
		int g = (c >> 8) & 0xff;
		int b = c & 0xff;
		return c << 16 + g << 8 + b;
	}

	/**
	 * Encapsulates
	 * Container for data, index, queue position, etc.
	 * Supports smoothing in the form of a moving average. To this end, the length of the 
	 * data array must be of size= size+smoothingSize so the first data item we want 
	 * to see can be smoothed, rather than skipping the first smoothingSize items. 
	 * TODO dynamic ordering / opacity based on mouse-over/highest value
	 * TODO max datapoint/history size (purged thereafter) & viewable datapoints,
	 * @author micycle1
	 *
	 */
	private class DataStream implements Comparable<DataStream> {

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
		 * @param datum datapoints
		 */
		public void push(float datum) {

			if (pointer == 0 && this.data[0] == Float.MAX_VALUE) { // on first data, generate moving average data, append to end of array because pointer starts at 0, so will look
																	// backwards for history
				System.out.println("fill");
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

			this.drawData[Math.floorMod(pointer - smoothing - 1, length + smoothing)] = constrain(drawData, 0,
					maxValue - 1) * (dimensions.y / maxValue); // constrain & scale (-1 is stroke Weight)

			pointer++; // inc pointer
			// pointer = ((pointer % length) + smoothing) % (length + smoothing); // recalc pointer (offset to active part of data array)
			pointer %= (length + smoothing);

			// smoothing - pointer
			pushedCount++;
		}

		public void pushEmpty() {
			push(-1); // TODO
		}

		/**
		 * Set new smoothing level
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
		 * Get draw data that is logically at the index given (where 0 is left most datapoint)
		 * or, ordered by recency, where 0 is oldest data point
		 * @param index
		 * @return
		 */
		public float getDrawData(int index) {
			int i = Math.floorMod(pointer + index - 1, length + smoothing); // -1, because pointer is incremented after push
			return drawData[i];
		}

		/**
		 * Get draw data that is logically at the index given (where 0 is left most datapoint)
		 * @param index
		 * @return
		 */
		public float getRawData(int index) { // TODO
			int i = Math.floorMod((pointer + index - 1) - (length), length + smoothing); // -1, because pointer is incremented after push
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

	/**
	 * Make own class, package protected
	 * TODO pane class (to encapsulate moving / resizing window (give it a pgraphics)? -- scrollmonitor extends... package private
	 * @author micycle1
	 *
	 */
	private class Pane {

		PGraphics canvas;

	}
}
