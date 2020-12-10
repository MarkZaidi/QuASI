/***
 * See https://github.com/MarkZaidi/QuPath-Image-Alignment/blob/main/Calculate-Transforms.groovy for most up-to-date version.
 * Please link to github repo when referencing code in forums, as the code will be continually updated.
 *
 * Script to align 2 or more images in the project with different pixel sizes, using either intensities or annotations.
 * Run from any image in a project containing all sets of images that require alignment
 * Writes the affine transform to an object inside the Affine subfolder of your project folder.
 * Also grabs the detection objects from the template image. Can change this to annotation objects.
 * Usage:
 * - Load in all sets of images to be aligned. Rename file names such that the only underscore (_) in the image name
 *   separates the SlideID from stain. Example: N19-1107 30Gy M5_PANEL2.vsi
 * - Adjust the inputs specified under "Needed inputs", and run script (can run on any image, iterates over entire project)
 *  - If script errors due to alignment failing to converge, set 'align_specific' to the SlideID of the image it failed on
 *  - Set 'skip_image' to 1, rerun script to skip over the error-causing image
 *  - Set 'skip_image' to 0, and either adjust 'AutoAlignPixelSize' or draw tissue annotations on all stains of images in list
 *  - run script, verify all moving images contain a transform file located in the 'Affine' folder
 *
 * Needed inputs:
 * - registrationType : Set as "AFFINE" for translations, rotations, scaling, and sheering. Set as "RIGID" for only translations and rotations.
 * - refStain : Set to stain name of image to align all subsequent images to
 * - wsiExt : file name extension
 * - align_specific : see above, set to null for first run through
 * - AutoAlignPixelSize : downsample factor when calculating the transform. Greater values result in faster calculation, but may impact quality
 * - skip_image see above, value doesn't matter if align_specific is null
 *
 *
 * Script largely adapted from Sara McArdle's callable implementation of QuPath's Interactive Image Alignment, and Yae Mun Lim's method
 * of matching reference (static) and overlay (moving) images based on file names.
 *
 */

import qupath.lib.objects.PathCellObject
import qupath.lib.objects.PathDetectionObject
import qupath.lib.objects.PathObject
import qupath.lib.objects.PathObjects
import qupath.lib.objects.PathTileObject
import qupath.lib.objects.classes.PathClassFactory
import qupath.lib.roi.RoiTools
import qupath.lib.roi.interfaces.ROI

import java.awt.geom.AffineTransform
import javafx.scene.transform.Affine
import qupath.lib.images.servers.ImageServer

import java.awt.Graphics2D
import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.TermCriteria;
import org.bytedeco.opencv.global.opencv_video;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.Indexer;

import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.servers.PixelCalibration;

import qupath.lib.regions.RegionRequest;
import qupath.opencv.tools.OpenCVTools

import java.awt.image.ComponentColorModel
import java.awt.image.DataBuffer

import static qupath.lib.gui.scripting.QPEx.*;

// Variables to set
//////////////////////////////////
String registrationType="RIGID" //Specify as "RIGID" or "AFFINE"
String refStain = "reference" //stain to use as reference image (all images will be aligned to this)
String wsiExt = ".qptiff" //image name extension
//def align_specific=['N19-1107 30Gy M5']//If auto-align on intensity fails, put the image(s) that it fails on here
def AutoAlignPixelSize = 400 //downsample factor for calculating transform (tform). Does not affect scaling of output image
align_specific=null
skip_image=0 // If 1, skips the images defined by 'align_specific'. If 0, skips all but image(s) in 'align_specific'
//Experimental features
use_single_channel=1 // Use a single channel from each image for alignment (set to channel number to use). Set to 0 to use all channels.

/////////////////////////////////


//Lim's code for file name matching
// Get list of all images in project
def projectImageList = getProject().getImageList()

// Create empty lists
def imageNameList = []
def slideIDList = []
def stainList = []
def missingList = []

// Split image file names to desired variables and add to previously created lists
for (entry in projectImageList) {
    def name = entry.getImageName()
    def (imageName, imageExt) = name.split('\\.')
    def (slideID, stain) = imageName.split('_')
    imageNameList << imageName
    slideIDList << slideID
    stainList << stain
}

// Remove duplicate entries from lists
slideIDList = slideIDList.unique()
stainList = stainList.unique()

// Remove specific entries if causing alignment to not converge
if (align_specific != null)
    if (skip_image == 1)
        slideIDList.removeAll(align_specific)
    else
        slideIDList.retainAll(align_specific)


if (stainList.size() == 1) {
    print 'Only one stain detected. Target slides may not be loaded.'
    return
}

// Create Affine folder to put transformation matrix files
path = buildFilePath(PROJECT_BASE_DIR, 'Affine')
mkdirs(path)

// Process all combinations of slide IDs, tissue blocks, and stains based on reference stain slide onto target slides
for (slide in slideIDList) {
    for (stain in stainList) {
        if (stain != refStain) {
            refFileName = slide + "_" + refStain + wsiExt
            targetFileName = slide + "_" + stain + wsiExt
            path = buildFilePath(PROJECT_BASE_DIR, 'Affine', targetFileName)
            def refImage = projectImageList.find {it.getImageName() == refFileName}
            def targetImage = projectImageList.find {it.getImageName() == targetFileName}
            if (refImage == null) {
                print 'Reference slide ' + refFileName + ' missing!'
                missingList << refFileName
                continue
            }
            if (targetImage == null) {
                print 'Target slide ' + targetFileName + ' missing!'
                missingList << targetFileName
                continue
            }
            println("Aligning reference " + refFileName + " to target " + targetFileName)
            //McArdle's code for image alignment
            ImageServer<BufferedImage> serverBase = refImage.readImageData().getServer()
            ImageServer<BufferedImage> serverOverlay = targetImage.readImageData().getServer()
            def static_img_name = refFileName
            def moving_img_name = targetFileName
            def project_name = getProject()
            def entry_name_static = project_name.getImageList().find { it.getImageName() == static_img_name }
            def entry_name_moving = project_name.getImageList().find { it.getImageName() == moving_img_name }

            def serverBaseMark = entry_name_static.readImageData()
            def serverOverlayMark = entry_name_moving.readImageData()
            Affine affine=[]

            //Perform the alignment. If no annotations present, use intensity. If annotations present, use area
            if(serverBaseMark.hierarchy.nObjects()>0||serverOverlayMark.hierarchy.nObjects()>0)
                autoAlignPrep(AutoAlignPixelSize,"AREA",serverBaseMark,serverOverlayMark,affine,registrationType,use_single_channel)
            else
                autoAlignPrep(AutoAlignPixelSize,"notAREA",serverBaseMark,serverOverlayMark,affine,registrationType,use_single_channel)



            def matrix = []
            matrix << affine.getMxx()
            matrix << affine.getMxy()
            matrix << affine.getTx()
            matrix << affine.getMyx()
            matrix << affine.getMyy()
            matrix << affine.getTy()

            new File(path).withObjectOutputStream {
                it.writeObject(matrix)
            }
        }
    }
}

if (missingList.isEmpty() == true) {
    print 'Done!'
} else {
    missingList = missingList.unique()
    print 'Done! Missing slides: ' + missingList
}


/*Subfunctions taken from here:
https://github.com/qupath/qupath/blob/a1465014c458d510336993802efb08f440b50cc1/qupath-experimental/src/main/java/qupath/lib/gui/align/ImageAlignmentPane.java
 */

//creates an image server using the actual images (for intensity-based alignment) or a labeled image server (for annotation-based).
double autoAlignPrep(double requestedPixelSizeMicrons, String alignmentMethod, ImageData<BufferedImage> imageDataBase, ImageData<BufferedImage> imageDataSelected, Affine affine,String registrationType, int use_single_channel) throws IOException {
    ImageServer<BufferedImage> serverBase, serverSelected;

    if (alignmentMethod == 'AREA') {
        logger.debug("Image alignment using area annotations");
        Map<PathClass, Integer> labels = new LinkedHashMap<>();
        int label = 1;
        labels.put(PathClassFactory.getPathClassUnclassified(), label++);
        for (def annotation : imageDataBase.getHierarchy().getAnnotationObjects()) {
            def pathClass = annotation.getPathClass();
            if (pathClass != null && !labels.containsKey(pathClass))
                labels.put(pathClass, label++);
        }
        for (def annotation : imageDataSelected.getHierarchy().getAnnotationObjects()) {
            def pathClass = annotation.getPathClass();
            if (pathClass != null && !labels.containsKey(pathClass))
                labels.put(pathClass, label++);
        }

        double downsampleBase = requestedPixelSizeMicrons / imageDataBase.getServer().getPixelCalibration().getAveragedPixelSize().doubleValue();
        serverBase = new LabeledImageServer.Builder(imageDataBase)
                .backgroundLabel(0)
                .addLabels(labels)
                .downsample(downsampleBase)
                .build();

        double downsampleSelected = requestedPixelSizeMicrons / imageDataSelected.getServer().getPixelCalibration().getAveragedPixelSize().doubleValue();
        serverSelected = new LabeledImageServer.Builder(imageDataSelected)
                .backgroundLabel(0)
                .addLabels(labels)
                .downsample(downsampleSelected)
                .build();
        //disable single channel alignment when working with Area annotations, unsure what bugs it can cause
        use_single_channel=0
    } else {
        // Default - just use intensities
        logger.debug("Image alignment using intensities");
        serverBase = imageDataBase.getServer();
        serverSelected = imageDataSelected.getServer();
    }

    scaleFactor=autoAlign(serverBase, serverSelected, registrationType, affine, requestedPixelSizeMicrons,use_single_channel);
    return scaleFactor
}

double autoAlign(ImageServer<BufferedImage> serverBase, ImageServer<BufferedImage> serverOverlay, String regionstrationType, Affine affine, double requestedPixelSizeMicrons, use_single_channel) {
    PixelCalibration calBase = serverBase.getPixelCalibration()
    double pixelSizeBase = calBase.getAveragedPixelSizeMicrons()
    double downsampleBase = 1
    if (!Double.isFinite(pixelSizeBase)) {
      //  while (serverBase.getWidth() / downsampleBase > 2000)
       //     downsampleBase++;
       // logger.warn("Pixel size is unavailable! Default downsample value of {} will be used", downsampleBase)
        pixelSizeBase=50
        downsampleBase = requestedPixelSizeMicrons / pixelSizeBase
    } else {
        downsampleBase = requestedPixelSizeMicrons / pixelSizeBase
    }

    PixelCalibration calOverlay = serverOverlay.getPixelCalibration()
    double pixelSizeOverlay = calOverlay.getAveragedPixelSizeMicrons()
    double downsampleOverlay = 1
    if (!Double.isFinite(pixelSizeOverlay)) {
    //    while (serverBase.getWidth() / downsampleOverlay > 2000)
    //        downsampleOverlay++;
     //   logger.warn("Pixel size is unavailable! Default downsample value of {} will be used", downsampleOverlay)
        pixelSizeOverlay=50
        downsampleOverlay = requestedPixelSizeMicrons / pixelSizeOverlay
    } else {
        downsampleOverlay = requestedPixelSizeMicrons / pixelSizeOverlay
    }

    double scaleFactor=downsampleBase/downsampleOverlay

    BufferedImage imgBase = serverBase.readBufferedImage(RegionRequest.createInstance(serverBase.getPath(), downsampleBase, 0, 0, serverBase.getWidth(), serverBase.getHeight()))
    BufferedImage imgOverlay = serverOverlay.readBufferedImage(RegionRequest.createInstance(serverOverlay.getPath(), downsampleOverlay, 0, 0, serverOverlay.getWidth(), serverOverlay.getHeight()))

    //Determine whether to calculate intensity-based alignment using all channels or a single channel
    Mat matBase
    Mat matOverlay
    if (use_single_channel==0) {
        //print 'using all channels'
        imgBase = ensureGrayScale(imgBase)
        imgOverlay = ensureGrayScale(imgOverlay)
        matBase = OpenCVTools.imageToMat(imgBase)
        matOverlay = OpenCVTools.imageToMat(imgOverlay)

    } else {

        matBase = OpenCVTools.imageToMat(imgBase)
        matOverlay = OpenCVTools.imageToMat(imgOverlay)
        int channel = use_single_channel-1
        //print ('using channel ' + channel)
        matBase = OpenCVTools.splitChannels(matBase)[channel]
        matOverlay = OpenCVTools.splitChannels(matOverlay)[channel]
        //use this to preview how the channel looks
        //OpenCVTools.matToImagePlus('Channel:' + channel.toString(), matBase).show()
    }


    /////pete code block/////

//// New bit
//    int channel = 2
//    matBase = OpenCVTools.splitChannels(matBase)[channel]
//    matOverlay = OpenCVTools.splitChannels(matOverlay)[channel]
//  ///end pete code block///

    Mat matTransform = Mat.eye(2, 3, opencv_core.CV_32F).asMat()
// Initialize using existing transform
//		affine.setToTransform(mxx, mxy, tx, myx, myy, ty)
    try {
        FloatIndexer indexer = matTransform.createIndexer()
        indexer.put(0, 0, (float)affine.getMxx())
        indexer.put(0, 1, (float)affine.getMxy())
        indexer.put(0, 2, (float)(affine.getTx() / downsampleBase))
        indexer.put(1, 0, (float)affine.getMyx())
        indexer.put(1, 1, (float)affine.getMyy())
        indexer.put(1, 2, (float)(affine.getTy() / downsampleBase))
//			System.err.println(indexer)
    } catch (Exception e) {
        logger.error("Error closing indexer", e)
    }

    TermCriteria termCrit = new TermCriteria(TermCriteria.COUNT, 100, 0.0001)

    try {
        int motion
        switch (regionstrationType) {
            case "AFFINE":
                motion = opencv_video.MOTION_AFFINE
                break
            case "RIGID":
                motion = opencv_video.MOTION_EUCLIDEAN
                break
            default:
                logger.warn("Unknown registraton type {} - will use {}", regionstrationType, RegistrationType.AFFINE)
                motion = opencv_video.MOTION_AFFINE
                break
        }
        double result = opencv_video.findTransformECC(matBase, matOverlay, matTransform, motion, termCrit, null)
        logger.info("Transformation result: {}", result)
    } catch (Exception e) {
        Dialogs.showErrorNotification("Estimate transform", "Unable to estimated transform - result did not converge")
        logger.error("Unable to estimate transform", e)
        return
    }

// To use the following function, images need to be the same size
//		def matTransform = opencv_video.estimateRigidTransform(matBase, matOverlay, false);
    Indexer indexer = matTransform.createIndexer()
    affine.setToTransform(
            indexer.getDouble(0, 0),
            indexer.getDouble(0, 1),
            indexer.getDouble(0, 2) * downsampleBase,
            indexer.getDouble(1, 0),
            indexer.getDouble(1, 1),
            indexer.getDouble(1, 2) * downsampleBase
    )
    indexer.release()

    matBase.release()
    matOverlay.release()
    matTransform.release()

    return scaleFactor
}

//to gather detection objects instead of annotation, change line ~250 to def pathObjects = otherHierarchy.getDetectionObjects()
def GatherObjects(boolean deleteExisting, boolean createInverse, File f){
    f.withObjectInputStream {
        matrix = it.readObject()

        // Get the project & the requested image name
        def project = getProject()
        def entry = project.getImageList().find {it.getImageName()+".aff" == f.getName()}
        if (entry == null) {
            print 'Could not find image with name ' + f.getName()
            return
        }

        def otherHierarchy = entry.readHierarchy()
        def pathObjects = otherHierarchy.getDetectionObjects() //OR getAnnotationObjects()

        // Define the transformation matrix
        def transform = new AffineTransform(
                matrix[0], matrix[3], matrix[1],
                matrix[4], matrix[2], matrix[5]
        )
        if (createInverse)
            transform = transform.createInverse()

        if (deleteExisting)
            clearAllObjects()

        def newObjects = []
        for (pathObject in pathObjects) {
            newObjects << transformObject(pathObject, transform)
        }
        addObjects(newObjects)
    }
}

//other subfunctions

PathObject transformObject(PathObject pathObject, AffineTransform transform) {
    // Create a new object with the converted ROI
    def roi = pathObject.getROI()
    def roi2 = transformROI(roi, transform)
    def newObject = null
    if (pathObject instanceof PathCellObject) {
        def nucleusROI = pathObject.getNucleusROI()
        if (nucleusROI == null)
            newObject = PathObjects.createCellObject(roi2, pathObject.getPathClass(), pathObject.getMeasurementList())
        else
            newObject = PathObjects.createCellObject(roi2, transformROI(nucleusROI, transform), pathObject.getPathClass(), pathObject.getMeasurementList())
    } else if (pathObject instanceof PathTileObject) {
        newObject = PathObjects.createTileObject(roi2, pathObject.getPathClass(), pathObject.getMeasurementList())
    } else if (pathObject instanceof PathDetectionObject) {
        newObject = PathObjects.createDetectionObject(roi2, pathObject.getPathClass(), pathObject.getMeasurementList())
        newObject.setName(pathObject.getName())
    } else {
        newObject = PathObjects.createAnnotationObject(roi2, pathObject.getPathClass(), pathObject.getMeasurementList())
        newObject.setName(pathObject.getName())
    }
    // Handle child objects
    if (pathObject.hasChildren()) {
        newObject.addPathObjects(pathObject.getChildObjects().collect({transformObject(it, transform)}))
    }
    return newObject
}

ROI transformROI(ROI roi, AffineTransform transform) {
    def shape = RoiTools.getShape(roi) // Should be able to use roi.getShape() - but there's currently a bug in it for rectangles/ellipses!
    shape2 = transform.createTransformedShape(shape)
    return RoiTools.getShapeROI(shape2, roi.getImagePlane(), 0.5)
}

static BufferedImage ensureGrayScale(BufferedImage img) {
    if (img.getType() == BufferedImage.TYPE_BYTE_GRAY)
        return img
    if (img.getType() == BufferedImage.TYPE_BYTE_INDEXED) {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY)
        def colorModel = new ComponentColorModel(cs, 8 as int[], false, true,
                Transparency.OPAQUE,
                DataBuffer.TYPE_BYTE)
        return new BufferedImage(colorModel, img.getRaster(), false, null)
    }
    BufferedImage imgGray = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_GRAY)
    Graphics2D g2d = imgGray.createGraphics()
    g2d.drawImage(img, 0, 0, null)
    g2d.dispose()
    return imgGray
}
