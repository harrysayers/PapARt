/* 
 * Copyright (C) 2014 Jeremy Laviole <jeremy.laviole@inria.fr>.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package fr.inria.papart.multitouch;

import fr.inria.papart.depthcam.DepthData;
import org.bytedeco.javacpp.opencv_core.IplImage;

import fr.inria.papart.procam.ARDisplay;
import fr.inria.papart.procam.Screen;
import fr.inria.papart.depthcam.Kinect;
import fr.inria.papart.depthcam.calibration.PlaneAndProjectionCalibration;
import fr.inria.papart.procam.Camera;
import fr.inria.papart.procam.BaseDisplay;
import fr.inria.papart.procam.ProjectiveDeviceP;
import fr.inria.papart.procam.ProjectorDisplay;
import fr.inria.papart.procam.camera.CameraOpenKinect;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;
import processing.core.PApplet;
import processing.core.PVector;
import toxi.geom.Matrix4x4;
import toxi.geom.Vec3D;

/**
 * Touch input, using a Kinect device for now.
 *
 * TODO: Refactor all this.
 *
 * @author jeremylaviole
 */
public class KinectTouchInput extends TouchInput {

    public static final int NO_TOUCH = -1;
    private int touch2DPrecision, touch3DPrecision;
    private Kinect kinect;
    private PApplet parent;

    private final Semaphore touchPointSemaphore = new Semaphore(1, true);
    private final Semaphore depthDataSem = new Semaphore(1);

// Tracking parameters
    static public final float trackNearDist = 30f;  // in mm
    static public final float trackNearDist3D = 70f;  // in mm

    // List of TouchPoints, given to the user
    private final CameraOpenKinect kinectCamera;

    private final PlaneAndProjectionCalibration calibration;

    // List of TouchPoints, given to the user
    ArrayList<TouchPoint> touchPoints2D = new ArrayList<TouchPoint>();
    ArrayList<TouchPoint> touchPoints3D = new ArrayList<TouchPoint>();
    private final TouchDetectionSimple2D touchDetection2D;
    private final TouchDetectionSimple3D touchDetection3D;

    public KinectTouchInput(PApplet applet,
            CameraOpenKinect kinectCamera,
            Kinect kinect,
            PlaneAndProjectionCalibration calibration) {
        this.parent = applet;
        this.kinect = kinect;
        this.kinectCamera = kinectCamera;
        this.calibration = calibration;
        this.touchDetection2D = new TouchDetectionSimple2D(Kinect.SIZE);
        this.touchDetection3D = new TouchDetectionSimple3D(Kinect.SIZE);
    }

    @Override
    public void update() {
        try {
            IplImage depthImage = kinectCamera.getDepthIplImage();
            depthDataSem.acquire();
            if (touch2DPrecision > 0 && touch3DPrecision > 0) {
                kinect.updateMT(depthImage, calibration, touch2DPrecision, touch3DPrecision);
                findAndTrack2D();
                findAndTrack3D();
            } else {
                if (touch2DPrecision > 0) {
                    kinect.updateMT2D(depthImage, calibration, touch2DPrecision);
                    findAndTrack2D();
                }
                if (touch3DPrecision > 0) {
                    kinect.updateMT3D(depthImage, calibration, touch3DPrecision);
                    findAndTrack3D();
                }
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(KinectTouchInput.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            depthDataSem.release();
        }
    }

    @Override
    public TouchList projectTouchToScreen(Screen screen, BaseDisplay display) {

        TouchList touchList = new TouchList();

        try {
            touchPointSemaphore.acquire();
        } catch (InterruptedException ie) {
            System.err.println("Semaphore Exception: " + ie);
        }

        for (TouchPoint tp : touchPoints2D) {
            try {
                Touch touch = createTouch(screen, display, tp);
                touchList.add(touch);
            } catch (Exception e) {
            }
        }

        for (TouchPoint tp : touchPoints3D) {
            try {
                Touch touch = createTouch(screen, display, tp);
                touchList.add(touch);
            } catch (Exception e) {
            }
        }

        touchPointSemaphore.release();
        return touchList;
    }

    private Touch createTouch(Screen screen, BaseDisplay display, TouchPoint tp) throws Exception {
        Touch touch = new Touch();

        if (useRawDepth) {
            projectPositionAndSpeedRaw(screen, display, touch, tp);
        } else {
            projectPositionAndSpeed(screen, display, touch, tp);
        }

        touch.is3D = tp.is3D();
        touch.touchPoint = tp;
        return touch;
    }

    // TODO: Raw Depth is for Kinect Only, find a cleaner solution.
    private ProjectiveDeviceP pdp;
    private boolean useRawDepth = false;

    public void useRawDepth(Camera camera) {
        this.useRawDepth = true;
        this.pdp = camera.getProjectiveDevice();
    }

    private void projectPositionAndSpeedRaw(Screen screen,
            BaseDisplay display,
            Touch touch, TouchPoint tp) throws Exception {

        Vec3D touchPosition = tp.getPositionKinect();

        PVector p = pdp.worldToPixelCoord(touchPosition);

        // Current point 
        PVector paperScreenCoord = project(screen, display,
                p.x / pdp.getWidth(),
                p.y / pdp.getHeight());

        paperScreenCoord.z = tp.getPosition().z;
        touch.position = paperScreenCoord;

        // Speed
        try {
            p = pdp.worldToPixelCoord(tp.getPreviousPositionKinect());
            paperScreenCoord = project(screen, display,
                    p.x / pdp.getWidth(),
                    p.y / pdp.getHeight());

            paperScreenCoord.z = tp.getPreviousPosition().z;
            touch.setPrevPos(paperScreenCoord);
        } catch (Exception e) {
            // Speed is set to 0
            touch.defaultPrevPos();
        }
    }

    private void projectPositionAndSpeed(Screen screen,
            BaseDisplay display,
            Touch touch, TouchPoint tp) throws Exception {

        PVector touchPositionNormalized = tp.getPosition();

        // Current point 
        PVector paperScreenCoord = project(screen, display,
                touchPositionNormalized.x,
                touchPositionNormalized.y);

        paperScreenCoord.z = tp.getPosition().z;
        touch.position = paperScreenCoord;
        // Speed
        try {
            float prevX = tp.getPreviousPosition().x;
            float prevY = tp.getPreviousPosition().y;
            paperScreenCoord = project(screen, display,
                    prevX,
                    prevY);
            paperScreenCoord.z = tp.getPreviousPosition().z;
            touch.setPrevPos(paperScreenCoord);
        } catch (Exception e) {
            // Speed is set to 0
            touch.defaultPrevPos();
        }
    }

    public ArrayList<Vec3D> projectDepthData(ARDisplay display, Screen screen) {
        try {

            depthDataSem.acquire();
            DepthData depthData = kinect.getDepthData();

            ArrayList<Vec3D> projected = new ArrayList<Vec3D>();
            Vec3D[] projPoints = depthData.projectedPoints;
            boolean isProjector = display instanceof ProjectorDisplay;

            for (int i = 0; i < projPoints.length; i++) {
                Vec3D vec = projPoints[i];
                if (vec == Kinect.INVALID_POINT) {
                    continue;
                }

                try {
                    PVector screenPosition = (isProjector ? (ProjectorDisplay) display : display).projectPointer(screen, vec.x, vec.y);
                    screenPosition.z = vec.z;
                    projected.add(vec);
                } catch (Exception e) {
                }

            }
            depthDataSem.release();
            return projected;
//                    res = (isProjector ? (Projector)display : display ).projectPointer(screen, tp);

        } catch (InterruptedException ex) {
            Logger.getLogger(KinectTouchInput.class
                    .getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

    public void getTouch2DColors(IplImage colorImage) {
        getTouchColors(colorImage, this.touchPoints2D);
    }

    public void getTouchColors(IplImage colorImage,
            ArrayList<TouchPoint> touchPointList) {

        if (touchPointList.isEmpty()) {
            return;
        }

        ByteBuffer cBuff = colorImage.getByteBuffer();

//        System.out.println("Searching for point color");
        for (TouchPoint tp : touchPointList) {
            int offset = 3 * kinect.findColorOffset(tp.getPositionKinect());

            tp.setColor((255 & 0xFF) << 24
                    | (cBuff.get(offset + 2) & 0xFF) << 16
                    | (cBuff.get(offset + 1) & 0xFF) << 8
                    | (cBuff.get(offset) & 0xFF));
        }

    }

    // Raw versions of the algorithm are providing each points at each time. 
    // no updates, no tracking. 
    public ArrayList<TouchPoint> find2DTouchRaw(int skip) {
        assert (skip > 0);
        return touchDetection2D.compute(kinect.getDepthData(), skip);
    }

    public ArrayList<TouchPoint> find3DTouchRaw(int skip) {
        assert (skip > 0);
        return touchDetection3D.compute(kinect.getDepthData(), skip);
    }

    protected void findAndTrack2D() {
        assert (touch2DPrecision != 0);
        ArrayList<TouchPoint> newList = touchDetection2D.compute(
                kinect.getDepthData(),
                touch2DPrecision);
        TouchPointTracker.trackPoints(touchPoints2D, newList,
                parent.millis(), trackNearDist);
    }

    protected void findAndTrack3D() {
        assert (touch3DPrecision != 0);
        ArrayList<TouchPoint> newList = touchDetection3D.compute(
                kinect.getDepthData(),
                touch3DPrecision);
        TouchPointTracker.trackPoints(touchPoints3D, newList,
                parent.millis(),
                trackNearDist3D);
    }

    public void setPrecision(int precision2D, int precision3D) {
        setPrecision2D(precision2D);
        setPrecision3D(precision3D);
    }

    public void setPrecision2D(int precision) {
        this.touch2DPrecision = precision;
    }

    public void setPrecision3D(int precision) {
        this.touch3DPrecision = precision;
    }

    public ArrayList<TouchPoint> getTouchPoints2D() {
        return this.touchPoints2D;
    }

    public ArrayList<TouchPoint> getTouchPoints3D() {
        return this.touchPoints3D;
    }

    public PlaneAndProjectionCalibration getCalibration() {
        return calibration;
    }

    public void lock() {
        try {
            touchPointSemaphore.acquire();
        } catch (Exception e) {
        }
    }

    public void unlock() {
        touchPointSemaphore.release();
    }

}