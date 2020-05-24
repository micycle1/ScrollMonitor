package micycle.scrollMonitor;

import static processing.core.PApplet.constrain;
import static processing.core.PApplet.max;
import static processing.core.PApplet.round;

import java.util.HashMap;

import processing.core.PApplet;
import processing.core.PShape;
import processing.core.PVector;
import processing.event.KeyEvent;

/**
 * TODO: datastream defines how many datapoints are visible from each stream
 * (global) or per-stream?
 * 
 * @author micycle1
 *
 */
public class ScrollMonitor extends ProcessingPane {

	private final HashMap<String, DataStream> streams;
	// private SortedMap<DataStream, Integer> todo; // TODO, ordered iteration

	private boolean pause = false;
	private int pauseFrameCount = 0; // used to pause BG scrolling

	private int graphStrokeWeight = 5; // TODO
	private int monitorBorderWeight = 2; // TODO
	
	int yAxisMax = 200; // TODO remove from datastream

	public ScrollMonitor(PApplet p, PVector position, PVector dimensions) {
		super(p, position, dimensions);
		streams = new HashMap<>();
	}

	/**
	 * 
	 * @param name       name/id reference
	 * @param historyCap how many datapoints the stream will display
	 */
	public void addDataStream(String name, int historyCap) {
		if (!streams.containsKey(name)) { // enfore unique name
			streams.put(name, new DataStream(name, historyCap, dimensions.copy()));
		}
	}

	/**
	 * Push data to a given datastream, identified by its name.
	 * 
	 * @param dataStreamName
	 * @param n              data
	 */
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

	/**
	 * Display colour for a data stream.
	 * 
	 * @param dataStream
	 * @param color
	 */
	public void setStreamColour(String dataStream, int color) {
		streams.get(dataStream).setColor(color);
	}

	@Override
	/**
	 * Draw into Pane's canvas
	 */
	public void draw() {

		unlockPosition();

		canvas.clear();
		
		boolean mouseOverGraph = false; // is any graph mouseover (enforce only one)

		drawBG(streams.values().iterator().next()); // TODO

		int dataStreamIndex = 0;
		for (DataStream d : streams.values()) { // TODO draw back to front
			PShape graph = p.createShape();
			graph.setStrokeWeight(graphStrokeWeight);
			if (d.fill) {
				graph.setFill(d.fillColour);
			} else {
				graph.noFill();
			}

			graph.beginShape(); // BEGIN GRAPH PSHAPE
			graph.fill(d.fillColour);
			if (!d.outline) {
				graph.noStroke(); // need to call inside beginShape()
			} else {
				graph.stroke(d.stroke); // set stroke colour
			}

			graph.vertex(-graphStrokeWeight * 2, (dimensions.y - d.getDrawData(0))); // start out of bounds (upper left)

			for (int i = 0; i < d.length; i++) {
				final float val = d.getDrawData(i);
				final float vertX = i * (dimensions.x / (d.length - 1)); // calc x coord -- scale to x dimension
				final float vertY = (dimensions.y) - val;
				graph.vertex(vertX, vertY);
			}

			graph.vertex(dimensions.x + graphStrokeWeight, (dimensions.y) - d.getDrawData(d.length - 1)); // draw out of
																										// bounds to
																										// hide stroke
			graph.vertex(dimensions.x + graphStrokeWeight, dimensions.y + graphStrokeWeight); // lower right corner
			graph.vertex(-graphStrokeWeight, dimensions.y + graphStrokeWeight); // lower left corner

			boolean mouseOverStream = false;
			if (!mouseOverGraph && withinMoveRegion && !dragging && pointInPoly(graph, PVector.sub(mousePos, position))) { // mouseOver
																										// test
				mouseOverGraph = true;
				mouseOverStream = true;
				lockPosition();
				graph.fill(0xff000000 | ~d.fillColour, canvas.alpha(d.fillColour)); // invert rgb, keep alpha TODO @150
			}

			graph.endShape(PApplet.CLOSE); // END GRAPH PSHAPE
			canvas.shape(graph);

//			canvas.fill(0);
//			p.fill(d.fillColour);
//			p.textAlign(PApplet.LEFT, PApplet.CENTER);
//			canvas.text(d.getDrawData(d.length - 1), 10 + (55 * dataStreamIndex), 30);
//			canvas.text(d.getRawData(d.length), 10 + (55 * dataStreamIndex), 50); // TODO

			if (mouseOverStream) {
				p.cursor(PApplet.CROSS);
				float x = constrain(mousePos.x, position.x + graphStrokeWeight - 1,
						position.x + dimensions.x - graphStrokeWeight + 1) - position.x; // constrain mouseOverX
				float valAtMouse = d.getDrawData((int) (x / (dimensions.x / (d.length - 1))));
				canvas.stroke(d.stroke);
				canvas.strokeWeight(max(1, graphStrokeWeight - 0));
				canvas.line(x, dimensions.y, x, dimensions.y - valAtMouse + 1); // hrzntl line where mouse is

				canvas.textAlign(PApplet.CENTER, PApplet.CENTER);
				canvas.fill(d.stroke);
				canvas.text(valAtMouse, x, PApplet.max(dimensions.y - valAtMouse - 25, 0));

				p.textAlign(PApplet.CENTER, PApplet.TOP);
				p.fill(0, 255, 0);
				p.text(round(valAtMouse) + d.dataUnit, x + position.x, position.y + dimensions.y + 10);
				canvas.text(d.name, 10 + (dataStreamIndex*20), 10); // display name of mouse-overed stream
			}
			else {
				p.fill(0); // black y-axis label
			}

			p.textAlign(PApplet.LEFT, PApplet.CENTER);
			
			p.text(round(d.paused ? d.getPauseValue() : d.getRawData(d.length)) + d.dataUnit, position.x + dimensions.x + 10,
					position.y + (dimensions.y - d.getDrawData(d.length - 1))); // y-axis val TODO color = ~bg

			dataStreamIndex++;
		}
	}

	@Override
	public void post() {
		p.noFill();
		p.strokeWeight(monitorBorderWeight * 2);
		p.rect(position.x - monitorBorderWeight, position.y - monitorBorderWeight, dimensions.x + 2 * monitorBorderWeight - 1,
				dimensions.y + 2 * monitorBorderWeight - 1);
	}

	@Override
	void resize() {
		for (DataStream d : streams.values()) {
			d.setDrawDimensions(dimensions.copy());
			d.setMaxValue(d.maxValue);
		}
	}

	@Override
	void move() {
		// TODO Auto-generated method stub
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
		pause = false;
		for (DataStream d : streams.values()) {
			d.resume();
		}
	}
	
	/**
	 * Pause a given dataStream from scrolling, display the data at pausing until
	 * resumed. It will still recieve data pushed in, but will not display until it
	 * is resumed.
	 */
	public void pauseDatastream(String dataStream) {
		streams.get(dataStream).pause();
	}

	/**
	 * Pause the view (data pushing is not blocked).
	 */
	public void unPauseDatastream(String dataStream) {
		streams.get(dataStream).resume();
	}
	
	public void hideDatastream(String datastream) {
		// TODO
	}

	public void showDatastream(String datastream) {
		// TODO
	}

	/**
	 * Pause the view (data pushing is not blocked).
	 * @see #pause()
	 * @see #unPause()
	 */
	public void togglePause() {
		// TODO
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
		// canvas.stroke(255, 255, 0); // TODO
		canvas.rect(0, 0, dimensions.x, dimensions.y); // BG

		canvas.stroke(0, 150); // guidelines
		canvas.strokeWeight(1); // guidelines

		float hSegments = 4; // graph segments, not lines
		for (int i = 0; i < hSegments + 1; i++) { // draw horizontal guidelines
			float y = dimensions.y - i * (dimensions.y / (hSegments + 0));
			canvas.line(0, y, dimensions.x, y);
			p.fill(0);
			p.textAlign(PApplet.RIGHT, PApplet.CENTER);
			p.text(round(yAxisMax / hSegments * i), position.x - 10, position.y + y);
		} // calc valuse based on stream max value Y

		float vSegments = 4; // graph segments, not lines
		for (int i = 0; i < vSegments; i++) { // draw vertical guidelines

			float xPos = Math.floorMod(
					(int) ((i * (dimensions.x / vSegments)
							- ((pause ? pauseFrameCount : p.frameCount) * dimensions.x / d.length))),
					(int) dimensions.x);
			canvas.line(xPos, 0, xPos, dimensions.y);
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
	 * Invert a RGBA color [0-255,0-255,0-255,0-255] 
	 * @param colour
	 * @return
	 */
	private static int invertColour(int colour) {
		return 0;
	}
}
