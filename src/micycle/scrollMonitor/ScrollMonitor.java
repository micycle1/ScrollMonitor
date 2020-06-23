package micycle.scrollMonitor;

import static processing.core.PApplet.constrain;
import static processing.core.PApplet.max;
import static processing.core.PApplet.round;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;

import processing.core.PApplet;
import processing.core.PShape;
import processing.core.PVector;
import processing.event.KeyEvent;

/**
 * TODO: datastream defines how many datapoints are visible from each stream
 * (global) or per-stream?{@link #addDataStream(String, int)}
 * 
 * TODO thread drawing, begin drawing when push called, then draw to papplet in
 * post().
 * 
 * TODO scroll wheel zoom x-axis; shft+scroll+mouseover zoom y-axis
 * 
 * TODO option to scroll if data not pushed (per time, or per frame) by adding empty data
 * 
 * @author micycle1
 *
 */
public class ScrollMonitor extends ProcessingPane {

	private final LinkedHashMap<String, DataStream> streams; // most recent last (add order)
	private LinkedList<DataStream> drawOrder; // most recent first (draw back to front)

	private boolean pause = false;
	private int pauseFrameCount = 0; // used to pause BG scrolling

	private int graphStrokeWeight = 5; // TODO (define per datastream?), "dynamic" option = maxVal+10%?
	private int monitorBorderWeight = 2; // TODO

	float yAxisMax = 200; // TODO remove from datastream
	int dataPoints = 300; // or x axis TODO remove from datastream
	
	private int bgSegmentsHorizontal = 4;
	private int bgSegmentsVertical = 4;

	public ScrollMonitor(PApplet p, PVector position, PVector dimensions, int yAxis) {
		super(p, position, dimensions);
		streams = new LinkedHashMap<>();
		drawOrder = new LinkedList<>();
		yAxisMax = yAxis;
	}

	/**
	 * Creates a new datastream within the monitor and assigns it a name. Use this
	 * name in to refer to that stream in other methods.
	 * 
	 * @param name       name/id reference
	 * @param historyCap how many datapoints the stream will display
	 */
	public void addDataStream(String name, int historyCap) {
		if (!streams.containsKey(name)) { // enfore unique name
			streams.put(name, new DataStream(name, historyCap, dimensions.copy()));
			drawOrder.offerFirst(streams.get(name)); // add to front of queue
			streams.get(name).setMaxValue(yAxisMax); // set draw max value
		} else {
			System.err.println("The data stream " + name + " is already present.");
		}
	}

	/**
	 * Removes a given datastream, given by its name, from the monitor.
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
	 * Pushes data to a given datastream, identified by its name.
	 * 
	 * @param dataStreamName
	 * @param n              data
	 */
	public void push(String dataStreamName, float n) {
		if (streams.containsKey(dataStreamName)) {
			streams.get(dataStreamName).push(n);
		} else {
			System.err.println("The data stream " + dataStreamName + " is not present and cannot be pushed to.");
		}
	}

	/**
	 * Sets the level of visual smoothing a stream has using a equal-weighted moving
	 * average of the last n terms TODO INCREASE STREAM SIZE BY SMOOTHING AMOUNT.
	 * This method is in contrast to {@link #setDrawSmoothing(int)}, which skips
	 * drawing every point; this method does not affect how points are drawn
	 * visually, but affects point data.
	 * 
	 * @param dataStreamName
	 * @param smoothing      default = 1 == no smoothing; must be >=1
	 */
	public void setStreamSmoothing(String dataStreamName, int smoothing) {
		streams.get(dataStreamName).setSmoothing(smoothing);
	}

	/**
	 * Sets the max/ceiling Y axis display value for the monitor. Datapoints with
	 * values greater than this will display but be capped visually.
	 * 
	 * @param value
	 * @see #setDynamicMaximum(DataStream)
	 */
	public void setMaxValue(float value) {
		yAxisMax = value;
		streams.values().forEach(stream -> stream.maxValue = value);
	}
	
	/**
	 * Sets the Y axis to dynamically scale its ceiling value based on the maximum
	 * value that is currently present in a given datastream. Rather than a fixed
	 * maximum Y axis value.
	 * 
	 * @param dataStream
	 * @see #setMaxValue(float)
	 */
	public void setDynamicYAxis(DataStream dataStream) {
		if (streams.containsKey(dataStream)) {
			// TODO
		} else {
			System.err.println("The data stream " + dataStream + " is not present.");
		}
	}

	/**
	 * Sets the display colour for a given data stream.
	 * 
	 * @param dataStream
	 * @param color      ARGB colour represented by an integer; use Processing's
	 *                   color() method to generate define values
	 */
	public void setStreamColour(String dataStream, int color) {
		if (streams.containsKey(dataStream)) {
			streams.get(dataStream).setColor(color);
		} else {
			System.err.println("The data stream " + dataStream + " is not present.");
		}
	}

	/**
	 * Level determines how many datapoints the scrollmonitor should skip when
	 * drawing the stroke for each graph (where 1 is draw every one, 2 is every
	 * other, etc.). Value of 2 can smooth line but may introduce visual stuttering
	 * as the graph scrolls.
	 * 
	 * @param level 1...5
	 */
	public void setDrawSmoothing(int level) {
		// TODO
	}

	public void setStreamUnit(String dataStream, String unit) {
		if (streams.containsKey(dataStream)) {
			streams.get(dataStream).dataUnit = unit;
		} else {
			System.err.println("The data stream " + dataStream + " is not present.");
		}
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

	@Override
	/**
	 * Draws into underlying Pane's canvas. Datastreams are drawn in the order of
	 * add (first added is drawn first, most recently added is drawn at the back).
	 */
	void draw() {

//		unlockPosition();

		canvas.clear();

		drawBG(); // TODO

		HashMap<DataStream, PShape> shapes = new HashMap<>(streams.size()); // cache PShapes; draw in reverse after

		for (DataStream d : streams.values()) { // populate PShapes -- order irrelevant
			if (d.draw) {
				PShape graphShape = p.createShape();
				graphShape.setStrokeWeight(graphStrokeWeight);

				graphShape.beginShape(); // BEGIN GRAPH PSHAPE

				if (!d.outline) {
					graphShape.noStroke(); // need to call inside beginShape()
				} else {
					graphShape.stroke(d.stroke); // set stroke colour
				}

				graphShape.vertex(-graphStrokeWeight * 2, (dimensions.y - d.getDrawData(0))); // start out of bounds
																								// (upper
																								// left)

				float val, vertX, vertY;
				for (int i = 0; i < d.length; i += 1) { // populate every other point (to smooth graph)
					val = d.getDrawData(i);
					vertX = i * (dimensions.x / (d.length - 1)); // calc x coord -- scale to x dimension
					vertY = (dimensions.y) - val;
					graphShape.vertex(vertX, vertY);
				}

				// draw out of bounds to hide stroke
				graphShape.vertex(dimensions.x + graphStrokeWeight, (dimensions.y) - d.getDrawData(d.length - 1));
				graphShape.vertex(dimensions.x + graphStrokeWeight, dimensions.y + graphStrokeWeight); // lower right
																										// corner
				graphShape.vertex(-graphStrokeWeight, dimensions.y + graphStrokeWeight); // lower left corner
				shapes.put(d, graphShape); // finished, but don't close/draw yet
			}
		} // end PShape for

		DataStream mouseOverStream = null; // TODO only check if mousepos different

		for (Iterator<DataStream> drawOrderReverse = drawOrder.descendingIterator(); drawOrderReverse.hasNext();) {
			DataStream d = drawOrderReverse.next();
			if (d.draw) {
				PShape s = shapes.get(d);
				if (withinMoveRegion && !dragging && mouseOverStream == null && pointInPoly(s, PVector.sub(mousePos, position))) {
					s.fill(0xff000000 | ~d.fillColour, canvas.alpha(d.fillColour) - 10); // invert col if mouse over
					mouseOverStream = d; // only one datastream can be mouseover (detect front to back)
				} else {
					if (d.fill) {
						s.fill(d.fillColour);
					} else {
						s.noFill();
					}
				}
				s.endShape(PApplet.CLOSE); // fill in shape
			}
		}

		p.textAlign(PApplet.LEFT, PApplet.CENTER);
		for (DataStream d : drawOrder) { // draw streams most recently added last (on bottom)
			if (d.draw) {
				canvas.shape(shapes.get(d));
				if (d == mouseOverStream) {
					p.fill(0, 255, 0); // TODO colour
				} else {
					p.fill(0);
				}
				p.text(round(d.getRawData(d.length)) + d.dataUnit, position.x + dimensions.x + 10,
						position.y + (dimensions.y - d.getDrawData(d.length - 1))); // y axis label
			}
		}

		if (mouseOverStream != null) { // draw mouseOverStream info (on top)
			p.cursor(PApplet.CROSS);
			float x = constrain(mousePos.x, position.x + graphStrokeWeight - 1, position.x + dimensions.x - graphStrokeWeight + 1)
					- position.x; // constrain mouseOverX
			int mouseOverIndex = (int) (x / (dimensions.x / (mouseOverStream.length - 1))); // index of point mouse is over
			float valAtMouse = mouseOverStream.getRawData(mouseOverIndex);
			float valAtMouseDrawLength = mouseOverStream.getDrawData(mouseOverIndex);

			canvas.stroke(mouseOverStream.stroke);
			canvas.strokeWeight(max(1, graphStrokeWeight - 0));
			canvas.line(x, dimensions.y, x, dimensions.y - valAtMouseDrawLength + 1); // vertical line where mouse is

			canvas.textAlign(PApplet.CENTER, PApplet.CENTER);
			canvas.fill(mouseOverStream.stroke);
			canvas.text(valAtMouse, x, PApplet.max(dimensions.y - valAtMouseDrawLength - 25, 0)); // mousePos label

			p.textAlign(PApplet.CENTER, PApplet.TOP);
			p.fill(0, 255, 0);
			p.text(round(valAtMouse) + mouseOverStream.dataUnit, x + position.x, position.y + dimensions.y + 10); // bottom label
			canvas.text(mouseOverStream.name, 10, 10); // display name of mouse-overed stream (top left)
		}
	}

	@Override
	void post() {
		p.noFill();
		p.strokeWeight(monitorBorderWeight * 2);
		p.rect(position.x - monitorBorderWeight, position.y - monitorBorderWeight, dimensions.x + 2 * monitorBorderWeight - 1,
				dimensions.y + 2 * monitorBorderWeight - 1);
	}

	@Override
	void resize() {
		for (DataStream d : streams.values()) {
			d.setDrawDimensions(dimensions);
			d.setMaxValue(d.maxValue);
		}
	}

	@Override
	void move() {
		p.noCursor(); // hide cursor when moving
	}

	@Override
	void mouseOver() {
		p.cursor(PApplet.MOVE); // over monitor but not over any graph
	}

	/**
	 * Pauses the view (data pushing is not blocked). [all]
	 * 
	 * @see #unPause()
	 */
	public void pause() {
		if (!pause) {
			pause = true;
			pauseFrameCount = p.frameCount;
			for (DataStream d : streams.values()) {
				d.pause();
			}
		}
	}

	/**
	 * Pauses the view (data pushing is not blocked).
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
	 * Pauses a given dataStream from scrolling, display the data at pausing until
	 * resumed. It will still recieve data pushed in, but will not display until it
	 * is resumed.
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
	 */
	public void hideDatastream(String dataStream) {
		if (streams.containsKey(dataStream)) {
			streams.get(dataStream).draw = false;
		} else {
			System.err.println("The data stream " + dataStream + " is not present.");
		}
	}

	public void showDatastream(String dataStream) {
		if (streams.containsKey(dataStream)) {
			streams.get(dataStream).draw = true;
		} else {
			System.err.println("The data stream " + dataStream + " is not present.");
		}
	}

	/**
	 * Pause the view (data pushing is not blocked).
	 * 
	 * @see #pause()
	 * @see #unPause()
	 */
	public void togglePause() {
		pause = !pause;
		pause();
		unPause();
	}

	@Override
	void keyReleased(KeyEvent e) {
		switch (e.getKeyCode()) {
			case 9 : // TAB
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
	 * Draws graph background (fill and line segments)
	 * 
	 * @param d
	 */
	private void drawBG() {
		canvas.fill(75, 150, 250, 200); // bg
//		canvas.noFill();
		canvas.noStroke();
//		canvas.stroke(0); // TODO
		canvas.rect(0, 0, dimensions.x, dimensions.y); // BG

		canvas.stroke(0, 150); // guidelines
		canvas.strokeWeight(1); // guidelines

		p.fill(0);
		p.textAlign(PApplet.RIGHT, PApplet.CENTER);
		for (int i = 0; i < bgSegmentsHorizontal; i++) { // draw horizontal guidelines
			float y = dimensions.y - i * (dimensions.y / (bgSegmentsHorizontal + 0));
			canvas.line(0, y, dimensions.x, y);
			p.text(round(yAxisMax / bgSegmentsHorizontal * i), position.x - 10, position.y + y);
		} // calc values based on stream max value Y
		p.text(round(yAxisMax / bgSegmentsHorizontal * bgSegmentsHorizontal), position.x - 10,
				position.y + dimensions.y - bgSegmentsHorizontal * (dimensions.y / (bgSegmentsHorizontal + 0))); // label, no line

		p.textAlign(PApplet.CENTER, PApplet.BOTTOM);
		float z = dataPoints / bgSegmentsVertical;
		for (int i = 0; i < bgSegmentsVertical; i++) { // draw vertical guidelines
			float xPos = Math.floorMod((int) ((i * (dimensions.x / bgSegmentsVertical)
					- ((pause ? pauseFrameCount : p.frameCount) * dimensions.x / dataPoints))), (int) dimensions.x);
			canvas.line(xPos, 0, xPos, dimensions.y);
			p.text((int) (p.frameCount - ((p.frameCount % dataPoints)) + (z * i)), position.x + xPos, position.y - 2 - monitorBorderWeight);
		}
	}

	/**
	 * Determine if a point is in a polygon, given by a lit of its vertices.
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

	/**
	 * Invert a RGBA color [0-255,0-255,0-255,0-255] TODO
	 * 
	 * @param colour
	 * @return
	 */
	private static int invertColour(int colour) {
		return 0;
	}
}
