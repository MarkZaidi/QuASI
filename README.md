# QuPath-Image-Alignment
List of QuPath scripts for alignment and stain deconvolution of whole-slide histology images
To do:
- verify script is robust
  - Can it handle aligning a project containing 2 mice and 3 stain sets (6 images in project total)? - yes
  - Does it successfully align and deconvolve H&E and DAB images? - yes to align, deconv is pending
  - Is it able to align based on annotations alone? - yes
  - Can it align largest image? (and at 1x downsample factor) - yes, but bug with writing image rotated on import
- Clean up scripts - done-ish
