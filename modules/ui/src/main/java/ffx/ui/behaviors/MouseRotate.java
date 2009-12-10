/**
 * <p>Title: Force Field X</p>
 * <p>Description: Force Field X is a Molecular Engineering Environment</p>
 * <p>Copyright: Copyright (c) Michael J. Schnieders 2002-2009</p>
 *
 * @author Michael J. Schnieders
 * @version 0.1
 *
 * This file is part of Force Field X.
 *
 * Force Field X is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as published
 * by the Free Software Foundation.
 *
 * Force Field X is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Force Field X; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */
package ffx.ui.behaviors;

import java.awt.AWTEvent;
import java.awt.event.MouseEvent;
import java.util.Enumeration;

import javax.media.j3d.Behavior;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.media.j3d.WakeupCriterion;
import javax.media.j3d.WakeupOnAWTEvent;
import javax.vecmath.Matrix4d;
import javax.vecmath.Vector3d;

// import ffe.panels.*;
/**
 * The MouseRotate class implements a mouse rotation behavior.
 */
public class MouseRotate extends MouseBehavior {
	private static Transform3D VPTG_T3D = new Transform3D();
	private static final Vector3d zero3d = new Vector3d(0.0, 0.0, 0.0);
	private static Vector3d translation = new Vector3d();
	private static Matrix4d mat = new Matrix4d();
	double x_angle, y_angle;
	double x_factor = 0.001;
	double y_factor = 0.001;
	int doneID = 0;
	private MouseBehaviorCallback callback = null;

	public MouseRotate(int flags, TransformGroup VPTG) {
		super(flags, VPTG);
	}

	public MouseRotate(int flags, TransformGroup VPTG, Behavior behavior,
			int postID, int dID) {
		super(flags, VPTG, behavior, postID);
		doneID = dID;
	}

	/*
	 * Return the x-axis movement multipler.
	 */
	public double getXFactor() {
		return x_factor;
	}

	/*
	 * Return the y-axis movement multipler.
	 */
	public double getYFactor() {
		return y_factor;
	}

	public void initialize() {
		super.initialize();
		x_angle = 0;
		y_angle = 0;
		if ((flags & INVERT_INPUT) == INVERT_INPUT) {
			invert = true;
			x_factor *= -1;
			y_factor *= -1;
		}
	}

	public void processStimulus(Enumeration criteria) {
		while (criteria.hasMoreElements()) {
			WakeupCriterion wakeup = (WakeupCriterion) criteria.nextElement();
			if (wakeup instanceof WakeupOnAWTEvent) {
				AWTEvent event[] = ((WakeupOnAWTEvent) wakeup).getAWTEvent();
				for (int i = 0; i < event.length; i++) {
					MouseEvent mevent = (MouseEvent) event[i];
					processMouseEvent(mevent);
					int id = event[i].getID();
					// Drag and Button 1 down
					if ((id == MouseEvent.MOUSE_DRAGGED)
							&& ((mevent.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) == MouseEvent.BUTTON1_DOWN_MASK)
							&& transformGroup != null) {
						x = ((MouseEvent) event[i]).getX();
						y = ((MouseEvent) event[i]).getY();
						int dx = x - x_last;
						int dy = y - y_last;
						if (!reset) {
							x_angle = dy * y_factor;
							y_angle = dx * x_factor;
							transformX.rotX(x_angle);
							transformY.rotY(y_angle);
							transformGroup.getTransform(currXform);
							currXform.get(mat);
							currXform.setTranslation(zero3d);
							if (ViewerTG != null) {
								ViewerTG.getTransform(VPTG_T3D);
								VPTG_T3D.setTranslation(zero3d);
								VPTG_T3D.invert();
								currXform.mul(VPTG_T3D, currXform);
							}
							if (invert) {
								currXform.mul(currXform, transformX);
								currXform.mul(currXform, transformY);
							} else {
								currXform.mul(transformX, currXform);
								currXform.mul(transformY, currXform);
							}
							if (ViewerTG != null) {
								VPTG_T3D.invert();
								currXform.mul(VPTG_T3D, currXform);
							}
							translation.set(mat.m03, mat.m13, mat.m23);
							currXform.setTranslation(translation);
							transformGroup.setTransform(currXform);
							transformChanged(currXform);
							if (callback != null) {
								callback.transformChanged(
										MouseBehaviorCallback.TRANSLATE,
										currXform);
							}
						} else {
							reset = false;
						}
						x_last = x;
						y_last = y;
					} else if (id == MouseEvent.MOUSE_PRESSED) {
						x_last = ((MouseEvent) event[i]).getX();
						y_last = ((MouseEvent) event[i]).getY();
					} else if (id == MouseEvent.MOUSE_RELEASED) {
						setTransformGroup(null);
					}
				}
			}
		}
		if (transformGroup != null || postCriterion == null) {
			wakeupOn(mouseCriterion);
		} else {
			postId(doneID);
			wakeupOn(postCriterion);
		}
	}

	/*
	 * Set the x-axis amd y-axis movement multipler with factor.
	 */
	public void setFactor(double factor) {
		x_factor = y_factor = factor;
	}

	/*
	 * Set the x-axis amd y-axis movement multipler with xFactor and yFactor
	 * respectively.
	 */
	public void setFactor(double xFactor, double yFactor) {
		x_factor = xFactor;
		y_factor = yFactor;
	}

	public void setupCallback(MouseBehaviorCallback c) {
		callback = c;
	}

	public void transformChanged(Transform3D transform) {
	}
}
/*
 * Copyright (c) 1996-1998 Sun Microsystems, Inc. All Rights Reserved. Sun
 * grants you ("Licensee") a non-exclusive, royalty free, license to use, modify
 * and redistribute this software in source and binary code form, provided that
 * i) this copyright notice and license appear on all copies of the software;
 * and ii) Licensee does not utilize the software in a manner which is
 * disparaging to Sun. This software is provided "AS IS," without a warranty of
 * any kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES,
 * INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN AND ITS LICENSORS SHALL
 * NOT BE LIABLE FOR ANY DAMAGES SUFFXRED BY LICENSEE AS A RESULT OF USING,
 * MODIFYING OR DISTRIBUTING THE SOFTWARE OR ITS DERIVATIVES. IN NO EVENT WILL
 * SUN OR ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR
 * DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES,
 * HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF THE
 * USE OF OR INABILITY TO USE SOFTWARE, EVEN IF SUN HAS BEEN ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGES. This software is not designed or intended for
 * use in on-line control of aircraft, air traffic, aircraft navigation or
 * aircraft communications; or in the design, construction, operation or
 * maintenance of any nuclear facility. Licensee represents and warrants that it
 * will not use or redistribute the Software for such purposes.
 */
