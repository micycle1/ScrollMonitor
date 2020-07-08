package micycle.scrollMonitor;

import static processing.core.PApplet.constrain;
import static processing.core.PApplet.round;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;

import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PShape;
import processing.core.PVector;
import processing.event.KeyEvent;

/**
 * A ScrollMonitor plots realtime data from any number of data sources (referred
 * to as datastreams). To use, first define a datastream using
 * {@link #addDataStream(String, int) addDataStream()} and then
 * {@link #push(String, float) push} data to that datastream. A scrollmonitor
 * steps once per frame.
 * 
 * 
 * <p>
 * TODO: datastream defines how many datapoints are visible from each stream
 * (global) or per-stream?{@link #addDataStream(String, int)}
 *
 * TODO scroll wheel zoom x-axis; shft+scroll+mouseover zoom y-axis
 * 
 * TODO option to scroll if data not pushed (per time, or per frame) by adding
 * empty data
 * 
 * @author micycle1
 *
 */
public class ScrollMonitor extends ProcessingPane {

	private final LinkedHashMap<String, DataStream> streams; // most recent last (add order)
	private LinkedList<DataStream> drawOrder; // most recent first (draw back to front)

	private boolean pause = false;
	private int pauseTime = 0; // used to pause BG scrolling

	private int graphStrokeWeight = 5;

	private int drawSmoothingLevel = 1;
	private int averageSmoothingLevel = 0;

	private float yAxisMax = 200; // Y-axis maximum value (ceiling)
	private int dataPoints = 300; // or x axis TODO remove from datastream

	private float xAxisPosition; // top (0) or bottom (1) of graph

	private int bgSegmentsHorizontal = 4;
	private int bgSegmentsVertical = 4;

	private int backgroundColour = -934570246; // default colour

	private int time = 0;
	private int timeStep = 1;

	/**
	 * The datastream, identified by name, that should be used to dynamically set
	 * the Y-axis maximum.
	 */
	private String yAxisDataStream = null;

	public ScrollMonitor(PApplet p, PVector position, PVector dimensions, int history, int yAxis) {
		super(p, position, dimensions);
		streams = new LinkedHashMap<>();
		drawOrder = new LinkedList<>();
		dataPoints = history;
		yAxisMax = yAxis;
		setXAxisPosition(0);
	}

	/**
	 * Creates a new datastream within the monitor and assigns it a name. Use this
	 * name as an identifier to refer to the stream in other methods.
	 * 
	 * @param name       name/id reference
	 * @param historyCap how many datapoints the stream will display
	 */
	public void addDataStream(String name) {
		if (!streams.containsKey(name)) { // enfore unique name
			streams.put(name, new DataStream(name, dataPoints, dimensions.copy(), averageSmoothingLevel));
			drawOrder.offerFirst(streams.get(name)); // add to front of queue
			streams.get(name).setMaxValue(yAxisMax); // set draw max value
		} else {
			System.err.println("The data stream " + name + " is already present.");
		}
	}

	/**
	 * Removes a given datastream, identified by its name, from the monitor.
	 * 
	 * @param name
	 */
	public void removeDataStream(String name) {
		if (streams.containsKey(name)) {
			drawOrder.remove(streams.get(name));
			streams.remove(name);
		} else {
			System.err.println("The data stream " + name + " is not present and cannot be removed.");
		}
	}

	/**
	 * Pushes a datum (single data point) to a given datastream, identified by its
	 * name.
	 * 
	 * @param dataStreamName
	 * @param datum          data
	 * @see #push(String, float[])
	 */
	public void push(String dataStreamName, float datum) {
		if (streams.containsKey(dataStreamName)) {
			streams.get(dataStreamName).push(datum);
		} else {
			System.err.println("The data stream " + dataStreamName + " is not present and cannot be pushed to.");
		}
	}

	/**
	 * Pushes multiple data points to a given datastream, identified by its name.
	 * 
	 * @param dataStreamName
	 * @param data           an array (float[]) or varargs (float, float,
	 *                       float......)
	 * @see #push(String, float)
	 */
	public void push(String dataStreamName, float... data) {
		if (streams.containsKey(dataStreamName)) {
			for (float datum : data) {
				streams.get(dataStreamName).push(datum);
			}
		} else {
			System.err.println("The data stream " + dataStreamName + " is not present and cannot be pushed to.");
		}
	}

	/**
	 * Sets the level of visual smoothing a stream has using a equal-weighted moving
	 * average of the last 'smoothing' terms. This method is in contrast to
	 * {@link #setDrawSmoothing(int)}, which skips drawing every point; this method
	 * does not affect how points are drawn visually, but affects point data.
	 * 
	 * @param dataStreamName
	 * @param smoothing      default = 0 == no smoothing; must be >=1
	 */
	public void setStreamDataSmoothing(String dataStream, int smoothing) {
		if (streams.containsKey(dataStream)) {
			streams.get(dataStream).setSmoothing(smoothing);
		} else {
			System.err.println("The data stream " + dataStream + " is not present.");
		}
	}

	/**
	 * Sets the length of a moving average window. Use this to smooth noisy data.
	 * 
	 * @param smoothing >=0. Default is 0 (no smoothing). Higher values
	 * @see #setStreamDataSmoothing(String, int)
	 */
	public void setDataSmoothing(int smoothing) {
		averageSmoothingLevel = constrain(smoothing, 0, dataPoints);
		streams.values().forEach(dataStream -> dataStream.setSmoothing(averageSmoothingLevel));
	}

	/**
	 * Sets the max/ceiling Y axis display value for the monitor. Datapoints with
	 * values greater than this will display but be capped visually.
	 * 
	 * @param value
	 * @see #setDynamicMaximum(DataStream)
	 */
	public void setMaxYAxisValue(float value) {
		yAxisMax = value;
		streams.values().forEach(stream -> stream.setMaxValue(yAxisMax));
		yAxisDataStream = null; // disable dynamic y-axis
	}

	/**
	 * Sets the Y axis to dynamically scale its ceiling value based on the maximum
	 * value that is currently present in a given datastream. Rather than a fixed
	 * maximum Y axis value.
	 * 
	 * @param dataStream
	 * @see #setMaxYAxisValue(float)
	 */
	public void setDynamicYAxis(String dataStream) {
		if (streams.containsKey(dataStream) || dataStream == null) {
			yAxisDataStream = dataStream;
		} else {
			System.err.println("The data stream " + dataStream + " is not present.");
		}
	}

	/**
	 * Sets the fill colour of a graph for a given data stream.
	 * 
	 * @param dataStream
	 * @param color      ARGB colour represented by an integer; use Processing's
	 *                   color() method to generate values
	 */
	public void setStreamFillColour(String dataStream, int color) {
		if (streams.containsKey(dataStream)) {
			streams.get(dataStream).fillColour = color;
		} else {
			System.err.println("The data stream " + dataStream + " is not present.");
		}
	}

	/**
	 * Sets the stroke (outline) colour for all graphs.
	 * 
	 * @param color ARGB colour represented by an integer; use Processing's color()
	 *              method to generate values
	 */
	public void setStrokeColour(int color) {
		streams.values().forEach(dataStream -> dataStream.strokeColour = color);
	}

	/**
	 * Sets the stroke (outline) colour of a graph for a given data stream.
	 * 
	 * @param dataStream
	 * @param color      ARGB colour represented by an integer; use Processing's
	 *                   color() method to generate values
	 */
	public void setStreamStrokeColour(String dataStream, int color) {
		if (streams.containsKey(dataStream)) {
			streams.get(dataStream).strokeColour = color;
		} else {
			System.err.println("The data stream " + dataStream + " is not present.");
		}
	}

	/**
	 * Sets the stroke (outline) weight (thickness) for all datastream graphs.
	 * 
	 * @param weight outline thickness; >= 0
	 */
	public void setStrokeWeight(int weight) {
		graphStrokeWeight = PApplet.max(0, weight);
	}

	/**
	 * Sets the monitor's background colour. Supports opacity.
	 * 
	 * @param colour ARGB colour represented by an integer; use Processing's color()
	 *               method to generate values
	 */
	public void setBackgroundColour(int colour) {
		backgroundColour = colour;
	}

	/**
	 * Sets the draw smoothing level. This level determines how many datapoints the
	 * ScrollMonitor should skip when drawing vertices in the graph for each data
	 * stream. A value of 1 means every data point is drawn (default value); a value
	 * of 2 means every other data point is drawn; a value of 3 means every third
	 * data point is drawn, and so on...
	 * 
	 * <p>
	 * Higher values result in a smoother line but may introduce visual stuttering
	 * as the graph scrolls. This value does not affect the underlying data, only
	 * how each graph is drawn.
	 * 
	 * @param level A value of 1 (default, no smoothing) or 2 is recommended.
	 */
	public void setDrawSmoothing(int smoothingLevel) {
		if (smoothingLevel < 1) {
			System.err.println("Smoothing level should be at least 1 (when set to 1, every datapoint is drawn).");
		} else {
			drawSmoothingLevel = smoothingLevel;
		}
	}

	/**
	 * Sets the data unit for a stream, given by its name. This unit is appended to
	 * the
	 * 
	 * @param dataStream
	 * @param unit       e.g. "FPS"
	 */
	public void setStreamUnit(String dataStream, String unit) {
		if (streams.containsKey(dataStream)) {
			streams.get(dataStream).dataUnit = unit;
		} else {
			System.err.println("The data stream " + dataStream + " is not present.");
		}
	}

	/**
	 * Sets whether the X-axis labels should be drawn above the monitor, or below
	 * the monitor.
	 * 
	 * @param position Above = 1; Below = 0. You can use Processing's TOP and BOTTOM
	 *                 constants too.
	 */
	public void setXAxisPosition(int position) {
		if (position == PConstants.TOP || position == 1) {
			xAxisPosition = 0;
			return;
		}
		if (position == PConstants.BOTTOM || position == 0) {
			xAxisPosition = dimensions.y + 2 * (2 + borderStrokeWeight);
			return;
		}
		System.err.println("An X-axis position value of " + position + " is not valid.");
	}

	/**
	 * How many segments the graph should be split into horizontally (setting to 2
	 * results in a single line spanning horizontally, splitting the BG into 2
	 * segments).
	 * 
	 * @param segments
	 */
	public void setHorizontalSegments(int segments) {
		if (segments < 0) {
			System.err.println("Segments number cannot be negative.");
		}
		bgSegmentsHorizontal = segments;
	}

	/**
	 * Defines how many vertical segments should be split into ( the amount of
	 * vertical lines drawn equals #verticalSegments - 1).
	 * 
	 * @param segments
	 */
	public void setVerticalSegments(int segments) {
		if (segments < 0) {
			System.err.println("Segments number cannot be negative.");
		}
		bgSegmentsVertical = segments;
	}

	/**
	 * Brings a given to the front of rendering (render on top of other streams).
	 * 
	 * @param dataStream
	 */
	public void bringToFront(String dataStream) {
		if (streams.containsKey(dataStream)) {
			DataStream d = streams.get(dataStream);
			drawOrder.remove(d);
			drawOrder.offerLast(d);
		} else {
			System.err.println("The data stream " + dataStream + " is not present.");
		}
	}

	/**
	 * Defines how many units (on the x-axis) the background should step every time
	 * {@link #run()} is called. By default, and will thereby match the parent
	 * PApplet framecount.
	 * 
	 * @param timeStep >=0; default = 1
	 * @see #stepTimeManually(int)
	 */
	public void setTimeStep(int timeStep) {
		this.timeStep = timeStep;
	}

	/**
	 * Steps the time (x-axis units) forward by a given amount.
	 * 
	 * <p>
	 * By default, a ScrollMonitor steps by current value of timeStep every time
	 * run() is called. This method, handy if you are pushing values to the
	 * ScrollMonitor sparingly (in conjunction with timeStep being set to 0).
	 * 
	 * @param units
	 * @see #setTimeStep(int)
	 */
	public void stepTimeManually(int units) {
		time += units;
	}

	@Override
	/**
	 * Draws into underlying Pane's canvas. Datastreams are drawn in the order of
	 * add (first added is drawn first, most recently added is drawn at the back).
	 */
	void draw() {

		canvas.clear();
		
		if (yAxisDataStream != null) {
			if (streams.containsKey(yAxisDataStream)) { // check hasn't been removed by removeDataStream()
				float yMax = Float.MIN_NORMAL;
				DataStream d = streams.get(yAxisDataStream);
				for (int i = 0; i < d.length; i++) { // TODO reference datapoints?
					float data = streams.get(yAxisDataStream).getRawData(i);
					yMax = data > yMax ? data : yMax; // set to max
				}
				if (yMax != yAxisMax) {
					setMaxYAxisValue(PApplet.lerp(yAxisMax, yMax * 1.01f, 0.05f)); // set to +1% greater
				}
			}
			else {
				yAxisDataStream = null; // reset (datastream ID not present)
			}
		}

		drawBG();

		HashMap<DataStream, PShape> shapes = new HashMap<>(streams.size()); // cache PShapes; draw in reverse after

		DataStream mouseOverStream = null; // The top-most datastream (graph) that the mouse is over

		for (Iterator<DataStream> drawOrderReverse = drawOrder.descendingIterator(); drawOrderReverse.hasNext();) {
			DataStream d = drawOrderReverse.next();
			if (d.draw) {
				canvas.fill(d.fillColour); // A workaround for OPENGL modes since shape.fill() doesn't work
				PShape graphShape = canvas.createShape();
				graphShape.setStrokeWeight(graphStrokeWeight);
				graphShape.beginShape(); // BEGIN GRAPH PSHAPE

				if (!d.outline) {
					graphShape.noStroke(); // need to call inside beginShape()
				} else {
					graphShape.stroke(d.strokeColour); // set stroke colour
				}

				// start shape out of bounds(upper left)
				graphShape.vertex(-graphStrokeWeight * 2, (dimensions.y - d.getDrawData(0)));

				float val, vertX, vertY;
				for (int i = 0; i < d.length; i += drawSmoothingLevel) { // drawSmoothingLevel
					val = d.getDrawData(i);
					vertX = i * (dimensions.x / (d.length - 1)); // calc x coord -- scale to x dimension
					vertY = (dimensions.y) - val;
					graphShape.vertex(vertX, vertY);
				}

				// draw out of bounds to hide stroke
				graphShape.vertex(dimensions.x + graphStrokeWeight, (dimensions.y) - d.getDrawData(d.length - 1));
				graphShape.vertex(dimensions.x + graphStrokeWeight, dimensions.y + graphStrokeWeight); // LR corner
				graphShape.vertex(-graphStrokeWeight, dimensions.y + graphStrokeWeight); // lower left corner

				// finished populating vertices, now check whether mouse is over graph pshape
				if (withinMoveRegion && !dragging && mouseOverStream == null && pointInPoly(graphShape, PVector.sub(mousePos, position))) {
					graphShape.fill(0xff000000 | ~d.fillColour, canvas.alpha(d.fillColour) - 5); // invert col if mouse over
					mouseOverStream = d; // only one datastream can be mouseover (detect front to back)
				} else {
					if (d.fill) {
						graphShape.fill(d.fillColour);
					} else {
						graphShape.noFill();
					}
				}
				graphShape.endShape(PApplet.CLOSE); // fill in shape
				shapes.put(d, graphShape);
			}
		}

		// Now draw graph shapes into canvas in reverse order
		p.textAlign(PApplet.LEFT, PApplet.CENTER);
		for (DataStream d : drawOrder) { // draw streams most recently added last (on bottom)
			if (d.draw) {
				canvas.shape(shapes.get(d));
				if (d == mouseOverStream) {
					p.fill(0, 155, 0);
				} else {
					p.fill(0);
				}
				p.text(round(d.getRawData(d.length)) + d.dataUnit, position.x + dimensions.x + 10,
						position.y + (dimensions.y - d.getDrawData(d.length - 1))); // y axis label (right side)
			}
		}

		if (mouseOverStream != null) { // draw mouseOverStream info (on top)
			p.cursor(PApplet.CROSS);
			float x = constrain(mousePos.x, position.x + graphStrokeWeight - 1, position.x + dimensions.x - graphStrokeWeight + 1)
					- position.x; // constrain mouseOverX
			int mouseOverIndex = (int) (x / (dimensions.x / (mouseOverStream.length - 1))); // index of point mouse is over
			float valAtMouse = mouseOverStream.getRawData(mouseOverIndex);
			float valAtMouseDrawLength = mouseOverStream.getDrawData(mouseOverIndex);

			canvas.stroke(mouseOverStream.strokeColour);
			canvas.strokeWeight(2);
			canvas.line(x, dimensions.y, x, dimensions.y - valAtMouseDrawLength + 1); // vertical line where mouse is

			canvas.textAlign(PApplet.CENTER, PApplet.CENTER);
			canvas.fill(mouseOverStream.strokeColour);
			canvas.text(valAtMouse, x, PApplet.max(dimensions.y - valAtMouseDrawLength - 25, 0)); // mousePos label

			p.textAlign(PApplet.CENTER, PApplet.TOP);
			p.fill(0, 255, 0);
			p.text(round(valAtMouse) + mouseOverStream.dataUnit, x + position.x, position.y + dimensions.y + 10); // bottom label
			canvas.text(mouseOverStream.name, 10, 10); // display name of mouse-overed stream (top left)
		}
	}

	@Override
	void post() {
		time += timeStep;
	}

	@Override
	void resize() {
		for (DataStream d : streams.values()) {
			d.drawDimensions = dimensions.copy();
//			d.setMaxValue(d.maxValue); // TODO
			d.recalcDrawData();
		}
		if (xAxisPosition != 0) {
			xAxisPosition = dimensions.y + 2 * (2 + borderStrokeWeight); // recalc x-axis height
		}
	}

	@Override
	void move() {
		if (renderer == RENDERERS.JAVA2D || renderer == RENDERERS.JAVAFX) {
			p.noCursor(); // hide cursor when moving (doesn't re-appear in OPENGL modes)
		}
	}

	@Override
	void mouseOver() {
		p.cursor(PApplet.MOVE); // cursor when mouse is over a monitor but not over any graph
	}

	@Override
	void keyReleased(KeyEvent e) {
		switch (e.getKeyCode()) {
			case PConstants.TAB : // TODO?
				if (pause) {
					unPause();
				} else {
					pause();
				}
				break;
			default :
				break;
		}
	}

	/**
	 * Pauses the view (data pushing is not blocked) for all datastreams.
	 * 
	 * @see #unPause()
	 * @see #togglePause()
	 */
	public void pause() {
		if (!pause) {
			pause = true;
			pauseTime = time;
			for (DataStream d : streams.values()) {
				d.pause();
			}
		}
	}

	/**
	 * Resumes graph scrolling for all datastreams.
	 * 
	 * @see #pause
	 * @see #togglePause()
	 */
	public void unPause() {
		if (pause) {
			pause = false;
			for (DataStream d : streams.values()) {
				d.resume();
			}
		}
	}

	/**
	 * (Un)pause the view (data pushing is not blocked).
	 * 
	 * @see #pause()
	 * @see #unPause()
	 */
	public void togglePause() {
		pause = !pause;
		pause();
		unPause();
	}

	/**
	 * Pauses a given dataStream from scrolling, displaying the data at pausing
	 * until resumed. The paused dataStream will still recieve data pushed to it,
	 * but will not display any new data until it is resumed.
	 */
	public void pauseDatastream(String dataStream) {
		if (streams.containsKey(dataStream)) {
			streams.get(dataStream).pause();
		} else {
			System.err.println("The data stream " + dataStream + " is not present.");
		}
	}

	/**
	 * Pauses the view (data pushing is not blocked).
	 */
	public void unPauseDatastream(String dataStream) {
		if (streams.containsKey(dataStream)) {
			streams.get(dataStream).resume();
		} else {
			System.err.println("The data stream " + dataStream + " is not present.");
		}
	}

	/**
	 * Hides a given data stream from being drawn within the monitor (it will still
	 * recieve any data pushed to it).
	 * 
	 * @param dataStream data stream to hide, given by its name identifier
	 * @see #showDatastream(String)
	 */
	public void hideDatastream(String dataStream) {
		if (streams.containsKey(dataStream)) {
			streams.get(dataStream).draw = false;
		} else {
			System.err.println("The data stream " + dataStream + " is not present.");
		}
	}

	/**
	 * Shows a datastream that has been {@link #hideDatastream(String) hidden}.
	 * 
	 * @param dataStream data stream to show, given by its name identifier
	 * @see #hideDatastream(String)
	 */
	public void showDatastream(String dataStream) {
		if (streams.containsKey(dataStream)) {
			streams.get(dataStream).draw = true;
		} else {
			System.err.println("The data stream " + dataStream + " is not present.");
		}
	}

	/**
	 * Draws graph background (fill and line segments)
	 * 
	 * @param d
	 */
	private void drawBG() {
		canvas.fill(backgroundColour); // bg fill
		canvas.noStroke();

		/**
		 * If BGcolour is fully opaque, call background() instead since it is faster.
		 */
		if (backgroundColour >> 24 == 255) {
			canvas.background(backgroundColour);
		} else {
			canvas.rect(0, 0, dimensions.x, dimensions.y);
		}

		canvas.stroke(0, 150); // guidelines
		canvas.strokeWeight(1); // guidelines

		p.fill(0); // text colour

		if (bgSegmentsHorizontal > 0) {
			p.textAlign(PApplet.RIGHT, PApplet.CENTER);
			for (int i = 0; i < bgSegmentsHorizontal; i++) { // draw horizontal guidelines
				float y = dimensions.y - i * (dimensions.y / (bgSegmentsHorizontal + 0));
				canvas.line(0, y, dimensions.x, y);
				p.text(round(yAxisMax / bgSegmentsHorizontal * i), position.x - 10, position.y + y);
			} // calc values based on stream max value Y
			p.text(round(yAxisMax / bgSegmentsHorizontal * bgSegmentsHorizontal), position.x - 10,
					position.y + dimensions.y - bgSegmentsHorizontal * (dimensions.y / (bgSegmentsHorizontal + 0))); // label, no line
		}

		if (bgSegmentsVertical > 0) {
			if (xAxisPosition == 0) {
				p.textAlign(PApplet.CENTER, PApplet.BOTTOM);
			} else {
				p.textAlign(PApplet.CENTER, PApplet.TOP);
			}
			float z = dataPoints / bgSegmentsVertical;
			for (int i = 0; i < bgSegmentsVertical; i++) { // draw vertical guidelines
				float xPos = Math.floorMod(
						(int) ((i * (dimensions.x / bgSegmentsVertical) - ((pause ? pauseTime : time) * dimensions.x / dataPoints))),
						(int) dimensions.x);
				canvas.line(xPos, 0, xPos, dimensions.y);
				p.text((int) (time - ((time % dataPoints)) + (z * i)), position.x + xPos,
						position.y - 2 - borderStrokeWeight + xAxisPosition); // draw x-axis labels
			}
		}
	}

	/**
	 * Determine if a point is in a polygon, given by a list of its vertices.
	 * 
	 * @param s     PShape
	 * @param point PVector point to check
	 * @return boolean
	 */
	private static final boolean pointInPoly(PShape s, PVector point) {

		boolean within = false;
		int j = s.getVertexCount() - 1;

		for (int i = 0; i < s.getVertexCount(); i++) {
			final PVector v = s.getVertex(i);
			final PVector b = s.getVertex(j);
			if (((v.y > point.y) != (b.y > point.y)) && (point.x < (b.x - v.x) * (point.y - v.y) / (b.y - v.y) + v.x)) {
				within = !within;
			}
			j = i;
		}
		return within;
	}
}
