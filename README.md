# QuPath-Image-Alignment
List of QuPath scripts for alignment and stain deconvolution of whole-slide histology images (WSIs). Output of `Calculate-Transforms.groovy` is a list of affine transformations that can be used to transform WSIs or annotations. Output of `Apply-Transforms.groovy` is a multichannel ome.tiff containing the transformed image channels appended (and optionally deconvolved if brightfield) to the reference image.
## Usage
### Calculate-Transforms.groovy
Use this script to generate transformation matrices for all images
- Load in all sets of images to be aligned into project. Rename file names such that the only underscore (_) in the image name separates the SlideID from stain. Example: N19-1107 30Gy M5_PANEL2.vsi. Make sure the SlideID is not contained within any other SlideID of an image set that is not intended to be aligned.
- Adjust the inputs specified under "Needed inputs", and run script (can run on any image, iterates over entire project)
- If script errors due to alignment failing to converge, set 'align_specific' to the SlideID of the image it failed on
- Set 'skip_image' to 1, rerun script to skip over the error-causing image
- Set 'skip_image' to 0, and either adjust 'AutoAlignPixelSize' or draw tissue annotations on all stains of images in list
- run script, verify all moving images contain a transform file located in the 'Affine' folder
### Alignment - StoreMatrix automatic.groovy (optional)
Use this script if you want to pull a transformation matrix from the `Interactive Image Alignment` GUI
- Open reference image in viewer
- Open the `Interactive Image Alignment` overlay, align an image
- While the overlay is still open, set 'name' to the name of the current moving image, and run script
### Apply-Transforms.groovy
Use this script to apply the transform to WSIs, appending them together in one large multichannel ome.tiff, optionally including separated stains if brightfield (H&E or HDAB)
 - Run 'Calculate-Transforms.groovy' to generate the necessary transform (tform) matrices required.
 - Set 'refStain' to the same reference stain as used in 'Calculate-Transforms.groovy'
 - Adjust 'variables to set' depending on the degree of downsampling, whether to display in viewer, or write out as an ome.tiff
 - Run script only on images containing 'refStain'. 
## To do:
- verify script is robust
  - Can it handle aligning a project containing 2 mice and 3 stain sets (6 images in project total)? - yes
  - Does it successfully align and deconvolve H&E and DAB images? - yes to align, deconv is pending
  - Is it able to align based on annotations alone? - yes
  - Can it align largest image? (and at 1x downsample factor) - yes, but bug with writing image rotated on import
  - select what channel(s) to align based on
- Bugs
  - when transforming an image set where one of the non-reference images has been rotated on import, ometiff gets somewhat corrupted during writing (tiles will fail during writing)
- Features
 - Offer ability to select what channel(s) to align based on
 - Improve string matching of 'filteredMap' in `Apply-Transforms.groovy` so that if a SlideID is contained within another SlideID of a different image that is not to be aligned to, this won't confuse the script and attempt to append the different image sets together


