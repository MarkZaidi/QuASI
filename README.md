# QuPath-Image-Alignment
List of QuPath scripts for alignment and stain deconvolution of whole-slide histology images
To do:
- verify script is robust
  - Can it handle aligning a project containing 2 mice and 3 stain sets (6 images in project total)?
  - Does it successfully align and deconvolve H&E and DAB images?
  - Is it able to align based on annotations alone?
  - Can it align largest image? (and at 1x downsample factor)
- Clean up scripts
- Document script
  - 2 parts, one for brightfield and one for if
    - for brightfield, must first set color deconvolution vectors, apply across all images in project.
  - first, make sure files are named appropriately. mouse_stainname
  - load all images into project. Including both the reference (static) and target (moving) images
  - set registrationtype, refstain, and wsiext accordingly in smardle_alignment
  - run smcardle_alignment script at appropriate requestedpixelsize
  - set refstain in readalignment_transform_append
  - run readalignment_transform_append at outputdownsample=20 and pathoutput=null to preview alignment
    - for those that have a poor alignment, draw tissue annotations using wand tool on original images. Then rerun smcardle_alignment for those images but change alignmenttype to area
  - once satisfied, rerun readalignment_transform_append to preview new transforms
  - if all transforms are sufficiently good, change outputdownsample to 1 and comment out pathoutput to write as a merged file in project directory
