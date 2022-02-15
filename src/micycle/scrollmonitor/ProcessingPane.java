package micycle.scrollmonitor;

import static processing.core.PApplet.constrain;
import static processing.core.PApplet.max;
import static processing.core.PApplet.min;

import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import processing.awt.PSurfaceAWT;
import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PGraphics;
import processing.core.PVector;
import processing.event.KeyEvent;
import processing.event.MouseEvent;

/**
 * A generic window pane to use for GUI windows/areas in Processing. A
 * ProcessingPane is ambivalent to the contents of its canvas (a PGraphics
 * object).
 * <p>
 * Concrete implementations (classes that extend ProcessingPane) should draw
 * into the pane's {@link #canvas}: e.g. <code>canvas.rect(a,b,c,d);</code>.
 * <p>
 * ProcessingPane has been developed in a *generic* manner for ScrollMonitor,
 * enabling easy use in any other unrelated projects.
 * 
 * @author Michael Carleton
 *
 */
abstract class ProcessingPane {

	/**
	 * Pane's parent PApplet.
	 */
	final PApplet p;
	final PGraphics canvas; // Pane graphics (sub-class should draw into this)
	/**
	 * Coordinate of upper-left corner (origin) of the pane.
	 */
	PVector position;
	/**
	 * Width & height of the pane.
	 */
	PVector dimensions;
	/**
	 * Position of mouse within parent PApplet.
	 */
	PVector mousePos;
	/**
	 * Position of mouse when the mouse was last pressed.
	 */
	PVector mouseDownPos;

	/**
	 * Tracks from which side(s) the monitor is being resized.
	 */
	private boolean[] resizeSides = new boolean[4]; // L,U,D,R
	boolean dragging = false, resizing = false; // Also exposed via method callbacks

	boolean drawBorder = false;
	int borderStrokeColor = 0;
	int borderStrokeWeight = 3;

	/**
	 * Rendering mode of parent PApplet (used when resizing to change mouse cursor)
	 */
	enum RENDERERS {
		JAVA2D, JAVAFX, JOGL
	}

	final RENDERERS renderer;

	private final PVector minimumDimensions; // Minimum pane resize dimensions

	private Scene pAppletFXscene = null;
	private java.awt.Canvas canvasAWT = null;

	/**
	 * Both used during resizing/moving the graph.
	 */
	private PVector cachePos, cacheDimensions;

	/**
	 * drag border bufferminimumDimensions The maximum (x,y) offset from sides the
	 * mouse can be
	 */
	private final PVector mouseResizeBuffer;

	/**
	 * Is mouse over the pane?
	 */
	boolean mouseOverPane = false, pmouseOverPane = false;

	/**
	 * Whether mouse is over a valid move region (a region smaller than
	 * {@link #mouseOverPane}).
	 */
	boolean withinMoveRegion = false;

	/**
	 * Allow/prevent end-user from moving/resizing pane.
	 */
	private boolean lockPosition = false, lockDimensions = false;

	/**
	 * Creates a new pane, having a minimum dimension of (50, 50).
	 * 
	 * @param p          parent PApplet (pane will listen to mouse and key events
	 *                   from this applet)
	 * @param position   coordinate of upper-left corner (origin) of the pane
	 * @param dimensions width and height of the pane
	 */
	public ProcessingPane(PApplet p, PVector position, PVector dimensions) {
		this.p = p;

		this.position = position;
		this.dimensions = dimensions;

		mouseResizeBuffer = new PVector(20, 20);
		minimumDimensions = new PVector(50, 50);

		switch (p.sketchRenderer()) {
			case "processing.javafx.PGraphicsFX2D" : // TODO Off-screen FX2D with Processing 4  
				canvas = p.createGraphics((int) dimensions.x, (int) dimensions.y);
				renderer = RENDERERS.JAVAFX;
				pAppletFXscene = ((Canvas) p.getSurface().getNative()).getScene();
				break;
			case "processing.awt.PGraphicsJava2D" :
				canvas = p.createGraphics((int) dimensions.x, (int) dimensions.y);
				renderer = RENDERERS.JAVA2D;
				canvasAWT = (java.awt.Canvas) ((PSurfaceAWT) p.getSurface()).getNative();
				break;
			case "processing.opengl.PGraphics3D" :
				canvas = p.createGraphics((int) dimensions.x, (int) dimensions.y, PConstants.P3D);
				renderer = RENDERERS.JOGL;
				break;
			case "processing.opengl.PGraphics2D" :
				canvas = p.createGraphics((int) dimensions.x, (int) dimensions.y, PConstants.P2D);
				renderer = RENDERERS.JOGL;
				break;
			default :
				canvas = p.createGraphics((int) dimensions.x, (int) dimensions.y);
				renderer = RENDERERS.JAVA2D;
		}
		canvas.smooth(3);

		p.registerMethod("mouseEvent", this);
		p.registerMethod("keyEvent", this);
	}

	/**
	 * Updates the pane and draws its contents into the parent PApplet.
	 * <p>
	 * Internally, this method calls {@link #draw()} followed by {@link #post()},
	 * which should contain the user implementation.
	 */
	public final void run() {
		update(); // resizing & dragging, etc.
		canvas.beginDraw();
		draw();
		canvas.endDraw();
		p.image(canvas, position.x, position.y);
		if (drawBorder) {
			p.stroke(borderStrokeColor);
			p.strokeWeight(borderStrokeWeight);
			p.line(position.x, position.y, position.x, position.y + dimensions.y);
			p.line(position.x + dimensions.x, position.y, position.x + dimensions.x, position.y + dimensions.y);
			p.line(position.x, position.y + dimensions.y, position.x + dimensions.x, position.y + dimensions.y);
			p.line(position.x, position.y, position.x + dimensions.x, position.y);
		}
		post();
	}

	/**
	 * Internal method to update things pertaining to the pane not the canvas it
	 * contains.
	 */
	private final void update() { // pre#

		mousePos = new PVector(constrain(p.mouseX, 0, p.width), constrain(p.mouseY, 0, p.height));
		mouseOverPane = withinRegion(mousePos, position, PVector.add(position, dimensions));
		withinMoveRegion = withinRegion(mousePos, PVector.add(position, mouseResizeBuffer),
				PVector.add(position, dimensions).sub(mouseResizeBuffer));

		if (resizing) {
			resizeInternal();
			resize(); // call user code
			return; // return early
		}

		if (dragging) {
			position.set(PVector.sub(mousePos, mouseDownPos).add(cachePos)); // set pos to PApplet mousePos
			move(); // call user code
			return; // return early
		}

		if (mouseOverPane) {
			if (withinMoveRegion && !lockPosition) {
				mouseOver();
			} else {
				if (!lockDimensions) {
					calcSidesMouseOver();
					switch (renderer) {
						case JAVA2D :
							setAWTCursor();
							break;
						case JAVAFX :
							setFX2DCursor();
							break;
						case JOGL : // set cursor to default
						default :
							p.cursor(PApplet.ARROW);
							break;
					}
				}
			}
		} else {
			if (pmouseOverPane) {
				// change only once on mouse exit so multiple panes don't reset cursor for
				// others
				p.cursor(PApplet.ARROW);
			}
		}
		pmouseOverPane = mouseOverPane;
	}

	abstract void draw();

	/**
	 * Draw stuff after canvas has been written to PApplet.
	 */
	void post() {
	}

	/**
	 * Called then pane is resized.
	 */
	void resize() {
	}

	/**
	 * Called when pane is being moved
	 */
	void move() {
	}

	/**
	 * Called when mouse is over pane (within move region, not border)
	 */
	void mouseOver() {
	}

	final void lockPosition() {
		lockPosition = true;
	}

	final void unlockPosition() {
		lockPosition = false;
	}

	final void lockDimensions() {
		lockDimensions = true;
	}

	final void unlockDimensions() {
		lockDimensions = false;
	}

	public final void showBorder() {
		drawBorder = true;
	}

	public final void hideBorder() {
		drawBorder = false;
	}

	public final void setBorderStrokeWeight(int weight) {
		borderStrokeWeight = max(0, weight);
	}

	public final void setBorderStrokeColor(int color) {
		borderStrokeColor = color;
	}

	private final void resizeInternal() {
		PVector posBound = PVector.add(cachePos, cacheDimensions).sub(minimumDimensions); // stops moving the monitor
																							// when
		// resizing past prior edge
		PVector newDims = PVector.add(cacheDimensions, cachePos).sub(mousePos);
		PVector newDims2 = PVector.sub(mousePos, cachePos);
		newDims.set(max(newDims.x, minimumDimensions.x), max(newDims.y, minimumDimensions.y));

		if (resizeSides[0]) { // LEFT SIDE
			if (resizeSides[1]) { // ↖
				position.set(min(mousePos.x, posBound.x), min(mousePos.y, posBound.y));
				dimensions.set(newDims.x, newDims.y);
			} else if (resizeSides[2]) { // ↙
				position.set(min(mousePos.x, posBound.x), position.y);
				dimensions.set(cacheDimensions.x - newDims2.x, max(newDims2.y, minimumDimensions.y));
			} else { // �?
				position.set(min(mousePos.x, posBound.x), position.y);
				dimensions.set(newDims.x, dimensions.y);
			}
		} else if (resizeSides[1]) { // TOP SIDE
			if (resizeSides[3]) { // ↗
				position.set(position.x, min(mousePos.y, posBound.y));
				dimensions.set(max(newDims2.x, minimumDimensions.x), newDims.y);
			} else { // ↑
				position.set(position.x, min(mousePos.y, posBound.y));
				dimensions.set(dimensions.x, newDims.y);
			}
		} else if (resizeSides[2]) { // BOTTOM SIDE
			if (resizeSides[3]) { // ↘
				dimensions.set(max(newDims2.x, minimumDimensions.x), max(newDims2.y, minimumDimensions.y));
			} else { // ↓
				dimensions.set(dimensions.x, max(newDims2.y, minimumDimensions.y));
			}
		} else if (resizeSides[3]) { // →
			dimensions.set(max(newDims2.x, minimumDimensions.x), dimensions.y);
		}
		canvas.setSize((int) dimensions.x, (int) dimensions.y);
		resize();
	}

	private final void calcSidesMouseOver() {
		resizeSides[0] = withinRegion(mousePos, position, PVector.add(position, new PVector(mouseResizeBuffer.x, dimensions.y))); // L
		resizeSides[1] = withinRegion(mousePos, position,
				PVector.add(position, new PVector(dimensions.x + mouseResizeBuffer.x, mouseResizeBuffer.y))); // U
		resizeSides[2] = withinRegion(mousePos, new PVector(0, dimensions.y - mouseResizeBuffer.y).add(position),
				new PVector(dimensions.x, dimensions.y).add(position)); // D
		resizeSides[3] = withinRegion(mousePos, new PVector(dimensions.x, 0).add(position),
				new PVector(dimensions.x - mouseResizeBuffer.x, dimensions.y).add(position)); // R
	}

	/**
	 * Sets correct edge cursor when parent PApplet is in FX2D rendering mode.
	 */
	private final void setFX2DCursor() {
		// L,U,D,R
		if (resizeSides[0]) { // LEFT SIDE
			if (resizeSides[1]) { // NW
				pAppletFXscene.setCursor(Cursor.NW_RESIZE);
			} else if (resizeSides[2]) { // ←
				pAppletFXscene.setCursor(Cursor.SW_RESIZE);
			} else { // â†�
				pAppletFXscene.setCursor(Cursor.W_RESIZE);
			}
		} else if (resizeSides[1]) { // TOP SIDE
			if (resizeSides[3]) { // â†—
				pAppletFXscene.setCursor(Cursor.NE_RESIZE);
			} else { // â†‘
				pAppletFXscene.setCursor(Cursor.N_RESIZE);
			}
		} else if (resizeSides[2]) { // BOTTOM SIDE
			if (resizeSides[3]) { // ↘
				pAppletFXscene.setCursor(Cursor.SE_RESIZE);
			} else { // ↓
				pAppletFXscene.setCursor(Cursor.S_RESIZE);
			}
		} else if (resizeSides[3]) { // ←
			pAppletFXscene.setCursor(Cursor.E_RESIZE);
		}
	}

	/**
	 * Sets correct edge cursor when parent PApplet is in JAVA2D (default) rendering
	 * mode.
	 */
	private final void setAWTCursor() {
		if (resizeSides[0]) { // LEFT SIDE
			if (resizeSides[1]) { // NW
				canvasAWT.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.NW_RESIZE_CURSOR));
			} else if (resizeSides[2]) { // â†™
				canvasAWT.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.SW_RESIZE_CURSOR));
			} else { // â†�
				canvasAWT.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.W_RESIZE_CURSOR));
			}
		} else if (resizeSides[1]) { // TOP SIDE
			if (resizeSides[3]) { // â†—
				canvasAWT.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.NE_RESIZE_CURSOR));
			} else { // â†‘
				canvasAWT.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.N_RESIZE_CURSOR));
			}
		} else if (resizeSides[2]) { // BOTTOM SIDE
			if (resizeSides[3]) { // â†˜
				canvasAWT.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.SE_RESIZE_CURSOR));
			} else { // â†“
				canvasAWT.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.S_RESIZE_CURSOR));
			}
		} else if (resizeSides[3]) { // �?
			canvasAWT.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.E_RESIZE_CURSOR));
		}
	}

	/**
	 * This method is <b>public</b> only to enable binding to a parent PApplet.
	 * <p>
	 * You can <b>ignore this method</b> since the parent sketch will call it
	 * automatically when it detects a mouse event (provided register() has been
	 * called).
	 */
	public void mouseEvent(MouseEvent e) {
		switch (e.getAction()) {
			case processing.event.MouseEvent.PRESS :
				mouseDownPos = new PVector(constrain(p.mouseX, 0, p.width), constrain(p.mouseY, 0, p.height));
				if (e.getButton() == PApplet.LEFT) {
					boolean mouseOverPane = withinRegion(mouseDownPos, position, PVector.add(position, dimensions));
					if (mouseOverPane) {
						boolean withinMoveRegion = withinRegion(mouseDownPos, PVector.add(position, mouseResizeBuffer),
								PVector.add(position, dimensions).sub(mouseResizeBuffer));
						cachePos = position.copy();
						if (withinMoveRegion && !lockPosition) { // moving
							dragging = true;
						} else { // resizing
							if (!lockDimensions) {
								resizing = true;
								cacheDimensions = dimensions.copy();
							}
						}
					}
				}
				mousePressed(e);
				break;
			case processing.event.MouseEvent.RELEASE :
				dragging = false;
				resizing = false;
				resizeSides = new boolean[4];
				mouseReleased(e);
				break;
			case processing.event.MouseEvent.CLICK :
				mouseDownPos = new PVector(constrain(p.mouseX, 0, p.width), constrain(p.mouseY, 0, p.height));
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
	 * Called automatically when the parent PApplet issues a <b>PRESS</b>
	 * {@link processing.event.MouseEvent MouseEvent}.
	 * <p>
	 * Therefore write any code here that should be executed when the mouse is
	 * <b>pressed</b>.
	 */
	void mousePressed(MouseEvent e) {
	}

	/**
	 * Called automatically when the parent PApplet issues a <b>RELEASE</b>
	 * {@link processing.event.MouseEvent MouseEvent}.
	 * <p>
	 * Therefore write any code here that should be executed when the mouse is
	 * <b>released</b>.
	 */
	void mouseReleased(MouseEvent e) {
	}

	/**
	 * Called automatically when the parent PApplet issues a <b>CLICK</b>
	 * {@link processing.event.MouseEvent MouseEvent}.
	 * <p>
	 * Therefore write any code here that should be executed when the mouse is
	 * <b>clicked</b>. (a press and release in quick succession).
	 */
	void mouseClicked(MouseEvent e) {
	}

	/**
	 * Called automatically when the parent PApplet issues a <b>WHEEL</b>
	 * {@link processing.event.MouseEvent MouseEvent}.
	 * <p>
	 * Therefore write any code here that should be executed when the mouse wheel is
	 * <b>scrolled</b>.
	 * <p>
	 * Use the getCount() method of the MouseEvent e parameter to get the scroll
	 * direction.
	 */
	void mouseWheel(MouseEvent e) {
	}

	/**
	 * Called automatically when the parent PApplet issues a <b>DRAG</b>
	 * {@link processing.event.MouseEvent MouseEvent}.
	 * <p>
	 * Therefore write any code here that should be executed when the mouse is
	 * <b>dragged</b>.
	 */
	void mouseDragged(MouseEvent e) {
	}

	/**
	 * This method is <b>Public</b> only to enable binding to a parent PApplet.
	 * <p>
	 * You can <b>ignore this method</b> since the parent sketch will call it
	 * automatically when it detects a key event (provided register() has been
	 * called).
	 */
	public void keyEvent(KeyEvent e) {
		switch (e.getAction()) {
			case processing.event.KeyEvent.PRESS :
				keyPressed(e);
				break;
			case processing.event.KeyEvent.RELEASE :
				keyReleased(e);
				break;
			default :
				break;
		}
	}

	/**
	 * Called automatically when the parent PApplet issues a <b>PRESS</b>
	 * {@link processing.event.KeyEvent KeyEvent}.
	 * <p>
	 * Therefore write any code here that should be executed when a key is
	 * <b>pressed</b>.
	 */
	void keyPressed(KeyEvent e) {
	}

	/**
	 * Called automatically when the parent PApplet issues a <b>RELEASE</b>
	 * {@link processing.event.KeyEvent KeyEvent}.
	 * <p>
	 * Therefore write any code here that should be executed when a key is
	 * <b>released</b>.
	 */
	void keyReleased(KeyEvent e) {
	}

	/**
	 * Determine if a point is within a rectangular region -- PVector params.
	 * 
	 * @param point PVector position to test.
	 * @param UL    Corner one of region.
	 * @param BR    Corner two of region (different X & Y).
	 * @return True if point contained in region.
	 */
	private static final boolean withinRegion(PVector point, PVector UL, PVector BR) {
		return (point.x >= UL.x && point.y >= UL.y) && (point.x <= BR.x && point.y <= BR.y) // SE
				|| (point.x >= BR.x && point.y >= BR.y) && (point.x <= UL.x && point.y <= UL.y) // NW
				|| (point.x <= UL.x && point.x >= BR.x) && (point.y >= UL.y && point.y <= BR.y) // SW
				|| (point.x <= BR.x && point.x >= UL.x) && (point.y >= BR.y && point.y <= UL.y); // NE
	}

}
