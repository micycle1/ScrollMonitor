package micycle.scrollMonitor;

import static processing.core.PApplet.constrain;
import static processing.core.PApplet.max;

import java.util.HashMap;

import processing.core.PApplet;
import processing.core.PImage;
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

	/**
	 * 
	 * @param name       name/id reference
	 * @param historyCap how many datapoints the stream will display
	 */
	public void addDataStream(String name, int historyCap) {
		streams.put(name, new DataStream(name, historyCap, dimensions.copy()));
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

		canvas.strokeCap(PApplet.ROUND);
		canvas.clear();

		drawBG(streams.values().iterator().next()); // TODO
		int strokeWeight = 5;
		canvas.strokeWeight(strokeWeight);
		int dataStreamIndex = 0;
		for (DataStream d : streams.values()) { // TODO draw back to front
			canvas.stroke(d.stroke);
			PShape graph = p.createShape();
			graph.setStrokeWeight(strokeWeight);
			if (d.fill) {
				graph.setFill(d.fillColour);
			} else {
				graph.noFill();
			}

			graph.beginShape(); // BEGIN GRAPH PSHAPE
			if (!d.outline) {
				graph.noStroke(); // need to call inside beginShape()
			} else {
				graph.stroke(d.stroke); // set stroke colour
			}

			graph.vertex(-strokeWeight * 2, (dimensions.y - d.getDrawData(0))); // start out of bounds (upper left)

			for (int i = 0; i < d.length; i++) {
				final float val = d.getDrawData(i);
				final float vertX = i * (dimensions.x / (d.length - 1)); // calc x coord -- scale to x dimension
				final float vertY = (dimensions.y) - val;
				graph.vertex(vertX, vertY);
			}

			graph.vertex(dimensions.x + strokeWeight, (dimensions.y) - d.getDrawData(d.length - 1)); // draw out of
																										// bounds to
																										// hide stroke
			graph.vertex(dimensions.x + strokeWeight, dimensions.y + strokeWeight); // lower right corner
			graph.vertex(-strokeWeight, dimensions.y + strokeWeight); // lower left corner

			boolean mouseOverStream = false;
			if (withinMoveRegion && pointInPoly(graph, PVector.sub(mousePos, position))) { // mouseOver test
				mouseOverStream = true;
				graph.fill(0xff000000 | ~d.fillColour, canvas.alpha(d.fillColour)); // invert rgb, keep alpha TODO @150
			}

			graph.endShape(PApplet.CLOSE); // END GRAPH PSHAPE
			canvas.shape(graph);

			canvas.fill(0);
			p.fill(d.fillColour);
			p.textAlign(PApplet.LEFT, PApplet.CENTER);
			canvas.text(d.getDrawData(d.length - 1), 10 + (55 * dataStreamIndex), 30);
			canvas.text(d.getRawData(d.length), 10 + (55 * dataStreamIndex), 50); // TODO

			p.text(PApplet.round(d.getRawData(d.length)) + d.dataUnit, position.x + dimensions.x + 10,
					position.y + (dimensions.y - d.getDrawData(d.length - 1)));

			if (mouseOverStream) {
				p.cursor(PApplet.CROSS);
				float x = constrain(mousePos.x, position.x + strokeWeight - 1,
						position.x + dimensions.x - strokeWeight + 1) - position.x; // constrain mouseOverX
				float valAtMouse = d.getDrawData((int) (x / (dimensions.x / (d.length - 1))));
				canvas.stroke(d.fillColour);
				canvas.strokeWeight(max(1, strokeWeight - 0));
				canvas.line(x, dimensions.y, x, dimensions.y - valAtMouse + (strokeWeight - 1)); // line where mouse is
				
				canvas.textAlign(PApplet.CENTER, PApplet.CENTER);
				canvas.fill(0, 255, 0);
				canvas.text(valAtMouse, x, PApplet.max(dimensions.y - valAtMouse - 25, 0));

				p.textAlign(PApplet.CENTER, PApplet.TOP);
				p.fill(0, 255, 0);
				p.text(PApplet.round(valAtMouse), x + position.x, position.y + dimensions.y + 10);
			}
			dataStreamIndex++;
		}
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
	 * Draws graph background (fill and line segments)
	 * 
	 * @param d
	 */
	private void drawBG(DataStream d) {
		canvas.fill(50, 125, 250, 150); // bg
		canvas.noStroke();
		// canvas.stroke(255, 255, 0); // TODO
		canvas.rect(0, 0, dimensions.x, dimensions.y); // bg

		canvas.stroke(0, 150); // guidelines
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
	 * Determine if a point is in a polygon, given by a lit of its vertices.
	 * 
	 * @param s     PShape
	 * @param point PVector point to check
	 * @return boolean
	 */
	private static boolean pointInPoly(PShape s, PVector point) {

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

}
