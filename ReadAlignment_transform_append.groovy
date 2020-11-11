/**
 * Merge images along the channels dimension in QuPath v0.2.0.
 *
 * This shows how multiple images can be combined by channel concatenation,
 * optionally applying color deconvolution or affine transformations along the way.
 * It may be applied to either brightfield images (with stains set) or fluorescence images.
 *
 * The result can be written to a file (if 'pathOutput' is defined) or opened in the QuPath viewer.
 *
 * Writing to a file is *strongly recommended* to ensure the result is preserved.
 * Opening in the viewer directly will have quite slow performance (as the transforms are applied dynamically)
 * and there is no guarantee the image can be reopened later, since the representation of the
 * transforms might change in future versions... so this is really only to preview results.
 *
 * Note QuPath does *not* offer full whole slide image registration - and there are no
 * plans to change this. If you require image registration, you probably need to use other
 * software to achieve this, and perhaps then import the registered images into QuPath later.
 *
 * Rather, this script is limited to applying a pre-defined affine transformation to align two or more
 * images. In the case where image registration has already been applied, it can be used to
 * concatenate images along the channel dimension without any addition transformation.
 *
 * In its current form, the script assumes you have an open project containing the images
 * OS-2.ndpi and OS-3.ndpi from the OpenSlide freely-distributable test data,
 * and the image type (and color deconvolution stains) have been set.
 * The script will apply a pre-defined affine transform to align the images (*very* roughly!),
 * and write their deconvolved channels together as a single 6-channel pseudo-fluorescence image.
 *
 * You will need to change the image names & add the correct transforms to apply it elsewhere.
 *
 * USE WITH CAUTION!
 * This uses still-in-development parts of QuPath that are not officially documented,
 * and may change or be removed in future versions.
 *
 * Made available due to frequency of questions, not readiness of code.
 *
 * For these reasons, I ask that you refrain from posting the script elsewhere, and instead link to this
 * Gist so that anyone requiring it can get the latest version.
 *
 * @author Pete Bankhead
 */

/**
* Mark's comments
* This script can be used to apply one or more affine transformations to one or more
* images, effectively aligning them to the first image listed in the 'transforms' variable.
*
* Prior to executing this script, a transformation matrix must be calculated in QuPath.
* This can be done through Analyze > Interactive Image Alignment. For automatic registration
* to converge, both images must have the same background color (i.e. black on black or white on white).
* If backgrounds are of different colors, manual registration can still work. Once an adequate transform is
* obtained, you can replace the transform listed in 'os3Transform'. 
*
* In 'transforms', the first image is the static image (does not move), and should have an identity transform following it in the array. To generate an
* identity transform, create a new AffineTransform(). Every subsequent image-transform pair can either contain an image followed by a transform,
* or an image followed by an identity transform (i.e. if you want to append a label image without transforming). 'pathOutput' denotes the path
* to write the transformed and appended image stack. I reccomend keeping the .ome.tif extension as that allows the pyramid to be retained.
*
* If you wish to downsample the output image, typically if you just want to debug the script, you can change 'outputDownsample'
* to the factor you wish to downsample by.
*
* By default, this script will attempt to perform stain deconvolution of any brightfield RGB images present. This may not be desirable
* if you want to append a RGB H&E for visualization purposes. I've set 'stains' to null near line 137 to prevent unwanted stain separation.
*
* If the transformed image seems drastically misaligned, the matrix might need to be inverted. This can be done by calling .createInverse()
* at the end of AffineTransformation
*/
import javafx.application.Platform
import org.locationtech.jts.geom.util.AffineTransformation
import qupath.lib.images.ImageData
import qupath.lib.images.servers.ImageChannel
import qupath.lib.images.servers.ImageServer
import qupath.lib.images.servers.ImageServers
import qupath.lib.roi.GeometryTools

import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.util.stream.Collectors

import static qupath.lib.gui.scripting.QPEx.*
import qupath.lib.images.servers.TransformedServerBuilder

// SET ME! Delete existing objects
def deleteExisting = true

// SET ME! Change this if things end up in the wrong place
def createInverse = true

// Specify reference stain
String refStain = "PANEL1"

import qupath.lib.objects.PathCellObject
import qupath.lib.objects.PathDetectionObject
import qupath.lib.objects.PathObject
import qupath.lib.objects.PathObjects
import qupath.lib.objects.PathTileObject
import qupath.lib.roi.RoiTools
import qupath.lib.roi.interfaces.ROI

import java.awt.geom.AffineTransform

import static qupath.lib.gui.scripting.QPEx.*

// Affine folder path
path = buildFilePath(PROJECT_BASE_DIR, 'Affine')

// Get list of all images in project
def projectImageList = getProject().getImageList()

def list_of_moving_image_names=[]
def list_of_transforms=[]
def list_of_reference_image_names=[]
// Read and obtain filenames from Affine folder
new File(path).eachFile{ f->
    f.withObjectInputStream {
        matrix = it.readObject()

        def targetFileName = f.getName()
        list_of_moving_image_names << targetFileName
        def (targetImageName, imageExt) = targetFileName.split('\\.')
        def (slideID, targetStain) = targetImageName.split('_')

        def targetImage = projectImageList.find {it.getImageName() == targetFileName}
        if (targetImage == null) {
            print 'Could not find image with name ' + f.getName()
            return
        }

        def targetImageData = targetImage.readImageData()
        def targetHierarchy = targetImageData.getHierarchy()

        refFileName = slideID + "_" + refStain + "." + imageExt
        list_of_reference_image_names << refFileName

        def refImage = projectImageList.find {it.getImageName() == refFileName}
        def refImageData = refImage.readImageData()
        def refHierarchy = refImageData.getHierarchy()

        def pathObjects = refHierarchy.getAnnotationObjects()

        //print 'Aligning objects from reference slide ' + refFileName + ' onto target slide ' + targetFileName

        // Define the transformation matrix
        def transform = new AffineTransform(
                matrix[0], matrix[3], matrix[1],
                matrix[4], matrix[2], matrix[5]
        )
        if (createInverse)
            transform = transform.createInverse()

        if (deleteExisting)
            targetHierarchy.clearAll()
        list_of_transforms << transform
        def newObjects = []
//        for (pathObject in pathObjects) {
//            newObjects << transformObject(pathObject, transform)
//        }
        //targetHierarchy.addPathObjects(newObjects)
        //targetImage.saveImageData(targetImageData)
    }
}
print 'Done!'
//print ([refFileName,refFileName])
list_of_reference_image_names=list_of_reference_image_names.unique()
//print list_of_reference_image_names
//print list_of_moving_image_names

//print list_of_moving_image_names
//print new AffineTransform()
//create linkedhashmap from list of image names and corresponding transforms
all_moving_file_map=[list_of_moving_image_names,list_of_transforms].transpose().collectEntries{[it[0],it[1]]}

print 'all_moving_file_map: ' + all_moving_file_map
//get currentImageName. NOTE, ONLY RUN SCRIPT ON REFERENCE IMAGES.
def currentImageName = getProjectEntry().getImageName()
print("Current image name: " + currentImageName);
if (!currentImageName.contains(refStain))
    print 'WARNING: non-reference image name detected. Only run script on reference images'
currentRefSlideName=currentImageName.split('_')
currentRefSlideName=currentRefSlideName[0]
print currentRefSlideName
def filteredMap= all_moving_file_map.findAll {it.key.contains(currentRefSlideName)}
print 'filteredmap: ' + filteredMap
def reference_transform_map = [
        (currentImageName) : new AffineTransform()
]
transforms=reference_transform_map + filteredMap
print 'transforms: ' + transforms

////Read in existing transforms
//def name2 = getProjectEntry().getImageName()
//def path2 = buildFilePath(PROJECT_BASE_DIR, 'Affine',name2)
////def path2 = buildFilePath(PROJECT_BASE_DIR,'Affine PIMO 19C 1 HE.tif','PIMO 19C PIMO 1.tif.aff')
//def matrix2 = null
//new File(path2).withObjectInputStream {
//    matrix2 = it.readObject()
//}

// Define a transform, e.g. with the (also unfinished) 'Interactive image alignment' command
// Note: you may need to remove .createInverse() depending upon how the transform is created
//def os3Transform = GeometryTools.convertTransform(new AffineTransformation([-0.9839072823524477,	0.28041312098503124,	13986.705393673828,
//-0.2606438398361206,	-0.9685527682304385,	25974.275657694427] as double[])).createInverse()
//print('os3:'+ os3Transform)
//def loaded_tform= GeometryTools.convertTransform(new AffineTransformation(matrix2 as double[])).createInverse()
//print('loadedtform' + loaded_tform)
// Define a map from the image name to the transform that should be applied to that image
//def transforms = [
//        (refFileName)    : new AffineTransform(), // Identity transform (use this if no transform is needed)
//        (list_of_moving_image_names[0]): list_of_transforms[0],
//        (list_of_moving_image_names[1]): list_of_transforms[1]
//]
//reg001_final.ome(1).tiff
//Exp_20200513_Region1_Spleen_H&E.tif
// Define an output path where the merged file should be written
// Recommended to use extension .ome.tif (required for a pyramidal image)
// If null, the image will be opened in a viewer
String pathOutput = null
//String pathOutput = buildFilePath(PROJECT_BASE_DIR, currentImageName + '.ome.tif')

// Choose how much to downsample the output (can be *very* slow to export large images with downsample 1!)
double outputDownsample = 10


// Loop through the transforms to create a server that merges these
def project = getProject()
def servers = []
def channels = []
int c = 0
for (def mapEntry : transforms.entrySet()) {
    // Find the next image & transform
    def name = mapEntry.getKey()
    print(name)
    def transform = mapEntry.getValue()
    if (transform == null)
        transform = new AffineTransform()
    def entry = project.getImageList().find {it.getImageName() == name}

    // Read the image & check if it has stains (for deconvolution)
    def imageData = entry.readImageData()
    def currentServer = imageData.getServer()
    def stains = imageData.getColorDeconvolutionStains()
    print(stains)
    
    // Nothing more to do if we have the identity trainform & no stains
    if (transform.isIdentity() && stains == null) {
        channels.addAll(updateChannelNames(name, currentServer.getMetadata().getChannels()))
        servers << currentServer
        continue
    } else {
        // Create a server to apply transforms
        def builder = new TransformedServerBuilder(currentServer)
        if (!transform.isIdentity())
            builder.transform(transform)
        // If we have stains, deconvolve them
        println(stains)
        stains=null // Mark's way of disabling stain deconvolution if a brightfield image is present
        if (stains != null) {
            builder.deconvolveStains(stains)
            for (int i = 1; i <= 3; i++)
                channels << ImageChannel.getInstance(name + "-" + stains.getStain(i).getName(), ImageChannel.getDefaultChannelColor(c++))
        } else {
            channels.addAll(updateChannelNames(name, currentServer.getMetadata().getChannels()))
        }
        servers << builder.build()
    }
}

println 'Channels: ' + channels.size()

// Remove the first server - we need to use it as a basis (defining key metadata, size)
ImageServer<BufferedImage> server = servers.remove(0)
// If anything else remains, concatenate along the channels dimension
if (!servers.isEmpty())
    server = new TransformedServerBuilder(server)
            .concatChannels(servers)
            .build()

// Write the image or open it in the viewer
if (pathOutput != null) {
    if (outputDownsample > 1)
        server = ImageServers.pyramidalize(server, outputDownsample)
    writeImage(server, pathOutput)
} else {
    // Create the new image & add to the project
    def imageData = new ImageData<BufferedImage>(server)
    setChannels(imageData, channels as ImageChannel[])
    Platform.runLater {
        getCurrentViewer().setImageData(imageData)
    }
}

// Prepend a base name to channel names
List<ImageChannel> updateChannelNames(String name, Collection<ImageChannel> channels) {
    return channels
            .stream()
            .map( c -> {
                return ImageChannel.getInstance(name + '-' + c.getName(), c.getColor())
                }
            ).collect(Collectors.toList())
}