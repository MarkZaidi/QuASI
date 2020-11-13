# QuPath-Image-Alignment
List of QuPath scripts for alignment and stain deconvolution of whole-slide histology images
To do:
- verify script is robust
  - Can it handle aligning a project containing 2 mice and 3 stain sets (6 images in project total)? - yes
  - Does it successfully align and deconvolve H&E and DAB images? - yes to align, deconv is pending
  - Is it able to align based on annotations alone? - yes
  - Can it align largest image? (and at 1x downsample factor) - yes, but bug with writing image rotated on import
- Bugs
  - when aligning an image set where one of the non-reference images has been rotated on import, ometiff gets somewhat corrupted during writing, more likely at lower downsample factors. Create a sample project containing the OS sample images (os-3 ndpi from the hosting server), post on forum
