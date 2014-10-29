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
package fr.inria.papart.depthcam;

import fr.inria.papart.depthcam.calibration.PlaneAndProjectionCalibration;
import org.bytedeco.javacpp.opencv_core.IplImage;
import fr.inria.papart.procam.ProjectiveDeviceP;
import java.nio.ByteBuffer;
import processing.core.PApplet;
import processing.core.PMatrix3D;
import processing.core.PVector;
import toxi.geom.Plane;
import toxi.geom.Vec3D;

/**
 * TODO: Kinect - Kinect 4 Processing - Kinect OpenCV - Kinect Multi-Touch With
 * inheritance !
 *
 * @author jeremy
 */
// TODO: 
//   use the Hardware calibration. 
public class Kinect {

// TODO: check theses...
    private float closeThreshold = 300f, farThreshold = 4000f;

    // Configuration 
    public ProjectiveDeviceP kinectCalibIR, kinectCalibRGB;

    // Protected values, important data. 
    protected float[] depthLookUp = null;

    // Raw data from the Kinect Sensor
    public int id;
    protected byte[] depthRaw;
    protected byte[] colorRaw;

    protected int[] connexity;  // TODO: check for Byte instead of int
    protected DepthData depthData;

    public static final int WIDTH = 640;
    public static final int HEIGHT = 480;
    public static final int SIZE = WIDTH * HEIGHT;

    public static PApplet papplet;

    public static final Vec3D INVALID_POINT = new Vec3D();

    ///// Modes
    public static final int KINECT_MM = 1;
    public static final int KINECT_10BIT = 0;
    private int mode;

    public Kinect(PApplet parent, String calibIR, String calibRGB) {
        this(parent, calibIR, calibRGB, KINECT_10BIT);
    }

    public Kinect(PApplet parent, String calibIR, String calibRGB, int mode) {
        Kinect.papplet = parent;
        this.mode = mode;
        try {
            kinectCalibRGB = ProjectiveDeviceP.loadCameraDevice(calibRGB, 0);
            kinectCalibIR = ProjectiveDeviceP.loadCameraDevice(calibIR, 0);
        } catch (Exception e) {
            System.out.println("Kinect init exception." + e);
        }
        init();
    }

    private PMatrix3D translateCam = new PMatrix3D(1, 0, 0, 5,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1);

    public int findColorOffset(Vec3D v) {
        PVector vt = new PVector(v.x, v.y, v.z);
        PVector vt2 = new PVector();
        //  Ideally use a calibration... 
//        kinectCalibRGB.getExtrinsics().mult(vt, vt2);       
        translateCam.mult(vt, vt2);
        return kinectCalibRGB.worldToPixel(new Vec3D(vt2.x, vt2.y, vt2.z));
    }

    public int findColorOffset(PVector v) {
        PVector vt = new PVector(v.x, v.y, v.z);
        PVector vt2 = new PVector();
        //  Ideally use a calibration... 
//        kinectCalibRGB.getExtrinsics().mult(vt, vt2);       
        translateCam.mult(vt, vt2);
        return kinectCalibRGB.worldToPixel(new Vec3D(vt2.x, vt2.y, vt2.z));
    }

//    public static 
    protected void init() {
        colorRaw = new byte[kinectCalibIR.getSize() * 3];
        depthRaw = new byte[kinectCalibIR.getSize() * 2];
        depthData = new DepthData(SIZE);
        depthData.projectiveDevice = this.kinectCalibIR;

        if (depthLookUp == null) {
            depthLookUp = new float[2048];
            if (this.mode == KINECT_10BIT) {
                for (int i = 0; i < depthLookUp.length; i++) {
                    depthLookUp[i] = rawDepthToMeters10Bits(i);
                }
            }
            if (this.mode == KINECT_MM) {
                for (int i = 0; i < depthLookUp.length; i++) {
                    depthLookUp[i] = rawDepthToMetersNoChange(i);
                }
            }
        }
    }

    public void setNearFarValue(float near, float far) {
        this.closeThreshold = near;
        this.farThreshold = far;
    }

    public void update(IplImage depth, int skip) {
        depthData.clearDepth();
        updateRawDepth(depth);
        computeDepthAndDo(skip, new DoNothing());
    }

    public void updateMT(IplImage depth, PlaneAndProjectionCalibration calib, int skip2D, int skip3D) {
        updateRawDepth(depth);
        depthData.clear();
        depthData.timeStamp = papplet.millis();
        depthData.planeAndProjectionCalibration = calib;
        computeDepthAndDo(1, new DoNothing());
        doForEachPoint(skip2D, new Select2DPointPlaneProjection());
        doForEachPoint(skip3D, new Select3DPointPlaneProjection());
    }

    public void updateMT2D(IplImage depth, PlaneAndProjectionCalibration calib, int skip) {
        updateRawDepth(depth);
        depthData.clearDepth();
        depthData.clear2D();
        depthData.timeStamp = papplet.millis();
        depthData.planeAndProjectionCalibration = calib;
        computeDepthAndDo(skip, new Select2DPointPlaneProjection());
    }

    public void updateMT3D(IplImage depth, PlaneAndProjectionCalibration calib, int skip) {
        updateRawDepth(depth);
        depthData.clearDepth();
        depthData.clear3D();
        depthData.timeStamp = papplet.millis();
        depthData.planeAndProjectionCalibration = calib;
        computeDepthAndDo(skip, new Select3DPointPlaneProjection());
    }

    protected void computeDepthAndDo(int precision, DepthPointManiplation manip) {
        for (int y = 0; y < kinectCalibIR.getHeight(); y += precision) {
            for (int x = 0; x < kinectCalibIR.getWidth(); x += precision) {

                int offset = y * kinectCalibIR.getWidth() + x;
                float d = getDepth(offset);
                if (d != INVALID_DEPTH) {
                    Vec3D pKinect = kinectCalibIR.pixelToWorld(x, y, d);
                    depthData.kinectPoints[offset] = pKinect;
                    manip.execute(pKinect, x, y, offset);
                }
            }
        }
    }

    protected void computeDepthAndDo(int precision, DepthPointManiplation manip, InvalidPointManiplation invalidManip) {
        for (int y = 0; y < kinectCalibIR.getHeight(); y += precision) {
            for (int x = 0; x < kinectCalibIR.getWidth(); x += precision) {

                int offset = y * kinectCalibIR.getWidth() + x;
                float d = getDepth(offset);
                if (d != INVALID_DEPTH) {
                    Vec3D pKinect = kinectCalibIR.pixelToWorld(x, y, d);
                    depthData.kinectPoints[offset] = pKinect;
                    manip.execute(pKinect, x, y, offset);
                } else {
                    invalidManip.execute(x, y, offset);
                }
            }
        }
    }

    protected void doForEachPoint(int precision, DepthPointManiplation manip) {
        for (int y = 0; y < kinectCalibIR.getHeight(); y += precision) {
            for (int x = 0; x < kinectCalibIR.getWidth(); x += precision) {
                int offset = y * kinectCalibIR.getWidth() + x;
                Vec3D pKinect = depthData.kinectPoints[offset];
                if (pKinect != INVALID_POINT) {
                    manip.execute(pKinect, x, y, offset);
                }
            }
        }
    }

    protected void doForEachValidPoint(int precision, DepthPointManiplation manip) {
        for (int y = 0; y < kinectCalibIR.getHeight(); y += precision) {
            for (int x = 0; x < kinectCalibIR.getWidth(); x += precision) {
                int offset = y * kinectCalibIR.getWidth() + x;
                Vec3D pKinect = depthData.kinectPoints[offset];
                if (pKinect != INVALID_POINT && depthData.validPointsMask[offset] == true) {
                    manip.execute(pKinect, x, y, offset);
                }
            }
        }
    }

    protected void updateRawDepth(IplImage depthImage) {
        ByteBuffer depthBuff = depthImage.getByteBuffer();
        depthBuff.get(depthRaw);
    }

    protected void updateRawColor(IplImage colorImage) {
        ByteBuffer colBuff = colorImage.getByteBuffer();
        colBuff.get(colorRaw);
    }

    static protected final float INVALID_DEPTH = -1;

    /**
     * @return the depth (float) or INVALID_DEPTH if it failed.
     */
    protected float getDepth(int offset) {
        float d = (depthRaw[offset * 2] & 0xFF) << 8
                | (depthRaw[offset * 2 + 1] & 0xFF);
        if (d >= 2047) {
            return INVALID_DEPTH;
        }
        d = 1000 * depthLookUp[(int) d];
        if (isGoodDepth(d)) {
            return d;
        } else {
            return INVALID_DEPTH;
        }
    }

    public interface InvalidPointManiplation {

        public void execute(int x, int y, int offset);
    }

    public interface DepthPointManiplation {

        public void execute(Vec3D p, int x, int y, int offset);
    }

    class Select2DPointPlaneProjection implements DepthPointManiplation {

        @Override
        public void execute(Vec3D p, int x, int y, int offset) {
            if (depthData.planeAndProjectionCalibration.hasGoodOrientationAndDistance(p)) {
                Vec3D projected = depthData.planeAndProjectionCalibration.project(p);
                depthData.projectedPoints[offset] = projected;
                if (isInside(projected, 0.f, 1.f, 0.0f)) {
                    depthData.validPointsMask[offset] = true;
                    depthData.validPointsList.add(offset);
                }
            }
        }
    }

    class SelectPlaneTouchHand implements DepthPointManiplation {

        @Override
        public void execute(Vec3D p, int x, int y, int offset) {

            boolean overTouch = depthData.planeAndProjectionCalibration.hasGoodOrientation(p);
            boolean underTouch = depthData.planeAndProjectionCalibration.isUnderPlane(p);
            boolean touchSurface = depthData.planeAndProjectionCalibration.hasGoodOrientationAndDistance(p);

            Vec3D projected = depthData.planeAndProjectionCalibration.project(p);

            if (isInside(projected, 0.f, 1.f, 0.0f)) {

                depthData.projectedPoints[offset] = projected;
                depthData.touchAttributes[offset] = new TouchAttributes(touchSurface, underTouch, overTouch);
                depthData.validPointsMask[offset] = touchSurface;

                if (touchSurface) {
                    depthData.validPointsList.add(offset);
                }
            }
        }
    }

    class Select2DPointOverPlane implements DepthPointManiplation {

        @Override
        public void execute(Vec3D p, int x, int y, int offset) {
            if (depthData.planeCalibration.hasGoodOrientation(p)) {
                depthData.validPointsMask[offset] = true;
                depthData.validPointsList.add(offset);
            }
        }
    }

    class Select2DPointCalibratedHomography implements DepthPointManiplation {

        @Override
        public void execute(Vec3D p, int x, int y, int offset) {

            PVector projected = new PVector();
            PVector init = new PVector(p.x, p.y, p.z);

            depthData.homographyCalibration.getHomographyInv().mult(init, projected);

            // TODO: Find how to select the points... 
            if (projected.z > 10 && projected.x > 0 && projected.y > 0) {
                depthData.validPointsMask[offset] = true;
                depthData.validPointsList.add(offset);
            }
        }
    }

    class Select3DPointPlaneProjection implements DepthPointManiplation {

        @Override
        public void execute(Vec3D p, int x, int y, int offset) {
            if (depthData.planeAndProjectionCalibration.hasGoodOrientation(p)) {
                Vec3D projected = depthData.planeAndProjectionCalibration.project(p);
                depthData.projectedPoints[offset] = projected;
                if (isInside(projected, 0.f, 1.f, 0.2f)) {
                    depthData.validPointsMask3D[offset] = true;
                    depthData.validPointsList3D.add(offset);
                }
            }
        }
    }

    class DoNothing implements DepthPointManiplation {

        @Override
        public void execute(Vec3D p, int x, int y, int offset) {

        }
    }

    public int getId() {
        return this.id;
    }

    public byte[] getColorBuffer() {
        return this.colorRaw;
    }

    public void undistortRGB(IplImage rgb, IplImage out) {
        kinectCalibRGB.getDevice().undistort(rgb, out);
    }

    // Not Working ! 
    /**
     * DO NOT USE - not working (distorsion estimation fail ?).
     *
     * @param ir
     * @param out
     */
    public void undistortIR(IplImage ir, IplImage out) {
        kinectCalibIR.getDevice().undistort(ir, out);
    }

    public ProjectiveDeviceP getColorProjectiveDevice() {
        return kinectCalibRGB;
    }

    public ProjectiveDeviceP getDepthProjectiveDevice() {
        return kinectCalibIR;
    }

    public boolean[] getValidPoints() {
        return depthData.validPointsMask;
    }

    public int[] getConnexity() {
        return this.connexity;
    }

    /**
     * Return the 3D points of the depth. 3D values in millimeters
     *
     * @return the array of 3D points.
     */
    public Vec3D[] getDepthPoints() {
        return depthData.kinectPoints;
    }

    public DepthData getDepthData() {
        return this.depthData;
    }

    public static float rawDepthToMeters10Bits(int depthValue) {
        if (depthValue < 2047) {
            return (float) (1.0 / ((float) (depthValue) * -0.0030711016f + 3.3309495161f));
        }
        return 0.0f;
    }

    public static float rawDepthToMeters10Bits2(int depthValue) {
        if (depthValue < 2047) {
            return 0.1236f * (float) Math.tan((double) depthValue / 2842.5 + 1.1863);
        }
        return 0.0f;
    }
    ////////////// WORKS WITH   DEPTH- REGISTERED - MM ////////

    public static float rawDepthToMetersNoChange(int depthValue) {
        if (depthValue < 2047) {
            return (float) depthValue / 1000f;
        }
        return 0.0f;
    }

    protected boolean isGoodDepth(float rawDepth) {
        return (rawDepth >= closeThreshold && rawDepth < farThreshold);
    }

    public static boolean isInside(Vec3D v, float min, float max, float sideError) {
        return v.x > min - sideError && v.x < max + sideError && v.y < max + sideError && v.y > min - sideError;
    }
}