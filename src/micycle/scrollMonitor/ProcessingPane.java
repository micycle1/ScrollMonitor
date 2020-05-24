package micycle.scrollMonitor;

import static processing.core.PApplet.constrain;
import static processing.core.PApplet.max;
import static processing.core.PApplet.min;

import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;

import processing.core.PApplet;
import processing.core.PGraphics;
import processing.core.PVector;

import processing.awt.PSurfaceAWT;
import processing.event.KeyEvent;
import processing.event.MouseEvent;

/**
 * A generic window pane to use for Processing GUI windows/areas. Ambivalent to
 * the contents of its canvas (PGraphics).
 * 
 * @author micycle1
 *
 */
abstract class ProcessingPane {

	PApplet p; // Parent PApplet (exposed for sub-class)
	PGraphics canvas; // Pane graphics (sub-class should draw into this)
	PVector position, dimensions;
	PVector mousePos, mouseDownPos;

	/**
	 * Tracks from which side(s) the monitor is being resized.
	 */
	private boolean[] resizeSides = new boolean[4]; // L,U,D,R
	boolean dragging = false, resizing = false; // Also exposed in method callbacks

	/**
	 * Rendering mode of parent PApplet (used when changing mouse cursor)
	 */
	private enum RENDERERS {
		JAVA2D, JAVAFX, JOGL
	}

	private final RENDERERS renderer;

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
	boolean mouseOverPane = false;

	/**
	 * is mouse over the valid move region (smaller than {@link #mouseOverPane})
	 */
	boolean withinMoveRegion = false;

	/**
	 * Allow/prevent end-user from moving/resizing pane.
	 */
	private boolean lockPosition = false, lockDimensions = false;

	public ProcessingPane(PApplet p, PVector position, PVector dimensions) {
		this.p = p;

		this.position = position;
		this.dimensions = dimensions;
		canvas = p.createGraphics((int) dimensions.x, (int) dimensions.y);
		canvas.smooth(4);

		mouseResizeBuffer = new PVector(20, 20);
		minimumDimensions = new PVector(150, 100);

		switch (p.sketchRenderer()) {
			case "processing.javafx.PGraphicsFX2D" :
				renderer = RENDERERS.JAVAFX;
				pAppletFXscene = ((Canvas) p.getSurface().getNative()).getScene();
				break;
			case "processing.awt.PGraphicsJava2D" :
				renderer = RENDERERS.JAVA2D;
				canvasAWT = (java.awt.Canvas) ((PSurfaceAWT) p.getSurface()).getNative();
				break;
			default :
				renderer = RENDERERS.JOGL;
				break;
		}

		p.registerMethod("mouseEvent", this);
		p.registerMethod("keyEvent", this);
	}

	public final void run() {
		update(); // resizing & dragging, etc.
		canvas.beginDraw();
		draw();
		canvas.endDraw();
		p.image(canvas, position.x, position.y);
		post();
	}

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
				p.cursor(PApplet.MOVE);
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
			p.cursor(PApplet.ARROW);
		}
	}

	public abstract void draw();

	/**
	 * Draw stuff after canvas has been written to PApplet.
	 */
	public void post() {
	};


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
			} else { // ←
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
		canvas = p.createGraphics((int) dimensions.x, (int) dimensions.y);
		resize();
	}

	/**
	 * Called then pane is resized.
	 */
	abstract void resize();

	/**
	 * Called when pane is being moved
	 */
	abstract void move();

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

	private void calcSidesMouseOver() {
		resizeSides[0] = withinRegion(mousePos, position,
				PVector.add(position, new PVector(mouseResizeBuffer.x, dimensions.y))); // L
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
	private void setFX2DCursor() {
		// L,U,D,R
		if (resizeSides[0]) { // LEFT SIDE
			if (resizeSides[1]) { // NW
				pAppletFXscene.setCursor(Cursor.NW_RESIZE);
			} else if (resizeSides[2]) { // â†™
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
	private void setAWTCursor() {
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
		} else if (resizeSides[3]) { // ←
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
							p.cursor(PApplet.MOVE);
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
				// moving();
				// resize();
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
	private static boolean withinRegion(PVector point, PVector UL, PVector BR) {
		return (point.x >= UL.x && point.y >= UL.y) && (point.x <= BR.x && point.y <= BR.y) // SE
				|| (point.x >= BR.x && point.y >= BR.y) && (point.x <= UL.x && point.y <= UL.y) // NW
				|| (point.x <= UL.x && point.x >= BR.x) && (point.y >= UL.y && point.y <= BR.y) // SW
				|| (point.x <= BR.x && point.x >= UL.x) && (point.y >= BR.y && point.y <= UL.y); // NE
	}

}
