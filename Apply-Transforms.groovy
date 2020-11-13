/**
 * See https://github.com/MarkZaidi/QuPath-Image-Alignment/blob/main/Apply-Transforms.groovy for most up-to-date version.
 * Please link to github repo when referencing code in forums, as the code will be continually updated.
 *
 * Script is largely adapted from Pete Bankhead's script:
 * https://gist.github.com/petebankhead/db3a3c199546cadc49a6c73c2da14d6c#file-qupath-concatenate-channels-groovy
 * with parts from Yae Mun Lim for file name matching
 *
 * Script reads in a set of Affine transformations generated from 'Calculate-Transforms.groovy' and applies them to
 * images in the project. The result is a multichannel image containing the reference image, with all aligned images
 * appended to it. Optionally, stain separation can be performed on brightfield images, although it is recommended that
 * you compute and set the color vectors as outlined in https://qupath.readthedocs.io/en/latest/docs/tutorials/separating_stains.html
 *
 * Usage:
 * - Run 'Calculate-Transforms.groovy' to generate the necessary transform (tform) matrices required.
 * - Set 'refStain' to the same reference stain as used in 'Calculate-Transforms.groovy'
 */
/** Comments from pete's script:
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


import javafx.application.Platform
import javafx.scene.transform.Affine
import qupath.lib.images.ImageData
import qupath.lib.images.servers.ImageChannel
import qupath.lib.images.servers.ImageServer
import qupath.lib.images.servers.ImageServers

import java.awt.image.BufferedImage
import java.util.stream.Collectors

import qupath.lib.images.servers.TransformedServerBuilder
import java.awt.geom.AffineTransform

import static qupath.lib.gui.scripting.QPEx.*
def currentImageName = getProjectEntry().getImageName()
// Variables to set
//////////////////////////////////////////////////////////////

def deleteExisting = true // SET ME! Delete existing objects
def createInverse = true // SET ME! Change this if things end up in the wrong place
String refStain = "PANEL3" // Specify reference stain, should be same as in 'Calculate-Transforms.groovy'
// Define an output path where the merged file should be written
// Recommended to use extension .ome.tif (required for a pyramidal image)
// If null, the image will be opened in a viewer
//String pathOutput = null
pathOutput = buildFilePath(PROJECT_BASE_DIR, currentImageName + '.ome.tif')
double outputDownsample = 20 // Choose how much to downsample the output (can be *very* slow to export large images with downsample 1!)

//////////////////////////////////////////////////////////////

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

//        def newObjects = []
//        for (pathObject in pathObjects) {
//            newObjects << transformObject(pathObject, transform)
//        }
        //targetHierarchy.addPathObjects(newObjects)
        //targetImage.saveImageData(targetImageData)
    }
}
list_of_reference_image_names=list_of_reference_image_names.unique()

//create linkedhashmap from list of image names and corresponding transforms
 all_moving_file_map=[list_of_moving_image_names,list_of_transforms].transpose().collectEntries{[it[0],it[1]]}

print 'all_moving_file_map: ' + all_moving_file_map
//get currentImageName. NOTE, ONLY RUN SCRIPT ON REFERENCE IMAGES.
print("Current image name: " + currentImageName);
if (!currentImageName.contains(refStain))
    print 'WARNING: non-reference image name detected. Only run script on reference images'
currentRefSlideName=currentImageName.split('_')
currentRefSlideName=currentRefSlideName[0]
print 'Processing: ' + currentRefSlideName
//Only keep entries that pertain to transforms relevant to images sharing the same SlideID and exclude any that contain
// refStain (there shouldn't be any with refStain generated as it's created below as an identity matrix, however running
// calculate-transforms.groovy with different refStains set can cause them to be generated, and override the identity matrix set below)

filteredMap= all_moving_file_map.findAll {it.key.contains(currentRefSlideName) && !it.key.contains(refStain)}
def reference_transform_map = [
        (currentImageName) : new AffineTransform()
]

transforms=reference_transform_map + filteredMap


// Loop through the transforms to create a server that merges these
def project = getProject()
def servers = []
def channels = []
int c = 0
for (def mapEntry : transforms.entrySet()) {
    print 'mapentry: ' + mapEntry
    
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
        //stains=null // Mark's way of disabling stain deconvolution if a brightfield image is present
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
