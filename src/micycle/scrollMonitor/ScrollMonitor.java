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
 * @author micycle1
 *
 */
public class ScrollMonitor extends ProcessingPane {

	private final LinkedHashMap<String, DataStream> streams; // most recent last (add order)
	private LinkedList<DataStream> drawOrder; // most recent first (draw back to front)

	private boolean pause = false;
	private int pauseFrameCount = 0; // used to pause BG scrolling

	private int graphStrokeWeight = 5; // TODO
	private int monitorBorderWeight = 2; // TODO

	int yAxisMax = 200; // TODO remove from datastream
	int dataPoints = 300; // or x axis TODO remove from datastream

	public ScrollMonitor(PApplet p, PVector position, PVector dimensions) {
		super(p, position, dimensions);
		streams = new LinkedHashMap<>();
		drawOrder = new LinkedList<>();
	}

	/**
	 * 
	 * @param name       name/id reference
	 * @param historyCap how many datapoints the stream will display
	 */
	public void addDataStream(String name, int historyCap) {
		if (!streams.containsKey(name)) { // enfore unique name
			streams.put(name, new DataStream(name, historyCap, dimensions.copy()));
			drawOrder.offerFirst(streams.get(name)); // add to front of queue
		} else {
			System.err.println("The data stream " + name + " is already present.");
		}
	}

	public void removeDataStream(String name) {
		if (streams.containsKey(name)) {
			drawOrder.remove(streams.get(name));
			streams.remove(name);
		} else {
			System.err.println("The data stream " + name + " is not present and cannot be removed.");
		}

	}

	/**
	 * Push data to a given datastream, identified by its name.
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
		if (streams.containsKey(dataStream)) {
			streams.get(dataStream).setMaxValue(value);
		} else {
			System.err.println("The data stream " + dataStream + " is not present.");
		}
	}

	/**
	 * Display colour for a data stream.
	 * 
	 * @param dataStream
	 * @param color
	 */
	public void setStreamColour(String dataStream, int color) {
		if (streams.containsKey(dataStream)) {
			streams.get(dataStream).setColor(color);
		} else {
			System.err.println("The data stream " + dataStream + " is not present.");
		}
	}

	/**
	 * Sets how many datapoints the stroke should skip (where 1 is draw every one, 2
	 * is every other, etc.). Value of 2 can smooth line but may introduce visual
	 * stuttering as the graph scrolls.
	 * 
	 * @param level 1...5
	 */
	public void setDrawSmoothing(int level) {
		// TODO
	}
	
	/**
	 * Bring a given to the front of rendering (render on top of other streams).
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
	 * Draw into Pane's canvas. Datastreams are drawn in the order of add (first
	 * added is drawn first, most recently added is drawn at the back).
	 */
	void draw() {

//		unlockPosition();

		canvas.clear();

		drawBG(drawOrder.getFirst()); // TODO

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
				for (int i = 0; i < d.length; i += 2) { // populate every other point (to smooth graph)
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
			DataStream d =  drawOrderReverse.next();
			if (d.draw) {
				PShape s = shapes.get(d);
				if (withinMoveRegion && !dragging && mouseOverStream == null
						&& pointInPoly(s, PVector.sub(mousePos, position))) {
					s.fill(0xff000000 | ~d.fillColour, canvas.alpha(d.fillColour) - 10);
					mouseOverStream = d; // only one datastream can be mouseover
				} else {
					if (d.fill) {
						s.fill(d.fillColour);
					} else {
						s.noFill();
					}
				}
				s.endShape(PApplet.CLOSE);
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
				p.text(round(d.paused ? d.getPauseValue() : d.getRawData(d.length)) + d.dataUnit,
						position.x + dimensions.x + 10, position.y + (dimensions.y - d.getDrawData(d.length - 1)));
			}
		}

		if (mouseOverStream != null) { // draw mouseOverStream info (on top)
			p.cursor(PApplet.CROSS);
			float x = constrain(mousePos.x, position.x + graphStrokeWeight - 1,
					position.x + dimensions.x - graphStrokeWeight + 1) - position.x; // constrain mouseOverX
			float valAtMouse = mouseOverStream.getDrawData((int) (x / (dimensions.x / (mouseOverStream.length - 1))));
			canvas.stroke(mouseOverStream.stroke);
			canvas.strokeWeight(max(1, graphStrokeWeight - 0));
			canvas.line(x, dimensions.y, x, dimensions.y - valAtMouse + 1); // hrzntl line where mouse is

			canvas.textAlign(PApplet.CENTER, PApplet.CENTER);
			canvas.fill(mouseOverStream.stroke);
			canvas.text(valAtMouse, x, PApplet.max(dimensions.y - valAtMouse - 25, 0));

			p.textAlign(PApplet.CENTER, PApplet.TOP);
			p.fill(0, 255, 0);
			p.text(round(valAtMouse) + mouseOverStream.dataUnit, x + position.x, position.y + dimensions.y + 10);
			canvas.text(mouseOverStream.name, 10, 10); // display name of mouse-overed stream
		}
	}

	@Override
	void post() {
		p.noFill();
		p.strokeWeight(monitorBorderWeight * 2);
		p.rect(position.x - monitorBorderWeight, position.y - monitorBorderWeight,
				dimensions.x + 2 * monitorBorderWeight - 1, dimensions.y + 2 * monitorBorderWeight - 1);
	}

	@Override
	void resize() {
		for (DataStream d : streams.values()) {
			d.setDrawDimensions(dimensions.copy());
			d.setMaxValue(d.maxValue);
		}
	}

	/**
	 * Pause the view (data pushing is not blocked). [all]
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
	 * Pause the view (data pushing is not blocked).
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
	 * Pause a given dataStream from scrolling, display the data at pausing until
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
	 * Pause the view (data pushing is not blocked).
	 */
	public void unPauseDatastream(String dataStream) {
		if (streams.containsKey(dataStream)) {
			streams.get(dataStream).resume();
		} else {
			System.err.println("The data stream " + dataStream + " is not present.");
		}
	}

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
	private void drawBG(DataStream d) {
		canvas.fill(50, 125, 250, 150); // bg
//		canvas.noFill();
		canvas.noStroke();
//		canvas.stroke(0); // TODO
		canvas.rect(0, 0, dimensions.x, dimensions.y); // BG

		canvas.stroke(0, 150); // guidelines
		canvas.strokeWeight(1); // guidelines

		float hSegments = 4; // graph segments, not lines
		p.fill(0);
		p.textAlign(PApplet.RIGHT, PApplet.CENTER);
		for (int i = 0; i < hSegments; i++) { // draw horizontal guidelines
			float y = dimensions.y - i * (dimensions.y / (hSegments + 0));
			canvas.line(0, y, dimensions.x, y);
			p.text(round(yAxisMax / hSegments * i), position.x - 10, position.y + y);
		} // calc valuse based on stream max value Y
		p.text(round(yAxisMax / hSegments * hSegments), position.x - 10,
				position.y + dimensions.y - hSegments * (dimensions.y / (hSegments + 0))); // label, no line

		p.textAlign(PApplet.CENTER, PApplet.BOTTOM);
		float vSegments = 4; // graph segments, not lines
		float z = dataPoints / vSegments;
		for (int i = 0; i < vSegments; i++) { // draw vertical guidelines

			float xPos = Math.floorMod(
					(int) ((i * (dimensions.x / vSegments)
							- ((pause ? pauseFrameCount : p.frameCount) * dimensions.x / d.length))),
					(int) dimensions.x);
			canvas.line(xPos, 0, xPos, dimensions.y);
			p.text((int) (p.frameCount - ((p.frameCount % dataPoints)) + (z * i)), position.x + xPos,
					position.y - 2 - monitorBorderWeight);
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
