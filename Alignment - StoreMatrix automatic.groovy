// From https://forum.image.sc/t/qupath-multiple-image-alignment-and-object-transfer/35521
//0.2.0m9
//Script writes out a file with the name of the current image, and the Affine Transformation in effect in the current viewer.
//Can get confused if there is more than one overlay active at once.
//Current image should be the destination image
// Michael Nelson 03/2020
import static qupath.lib.gui.scripting.QPEx.*
import qupath.ext.align.gui.ImageServerOverlay
/*
Usage:
- Open reference image in viewer
- Open the `Interactive Image Alignment` overlay, align an image
- While the overlay is still open, set `name` to the name of the current moving image, and run script
 */

//def name = getProjectEntry().getImageName()
////////////////////

def name='D1_PIMO' //specify name of moving (transform) image, as listed in the project

////////////////////
path = buildFilePath(PROJECT_BASE_DIR, 'Affine')
mkdirs(path)
path = buildFilePath(PROJECT_BASE_DIR, 'Affine', name)




def overlay = getCurrentViewer().getCustomOverlayLayers().find {it instanceof ImageServerOverlay}

affine = overlay.getAffine()

print affine
afString = affine.toString()
afString = afString.minus('Affine [').minus(']').trim().split('\n')
cleanAffine =[]
afString.each{
    temp = it.split(',')
    temp.each{cleanAffine << Double.parseDouble(it)}
}

def matrix = []
affineList = [0,1,3,4,5,7]
for (i=0;i<12; i++){
if (affineList.contains(i))
    matrix << cleanAffine[i]
}

new File(path).withObjectOutputStream {
    it.writeObject(matrix)
}
print 'Done!'