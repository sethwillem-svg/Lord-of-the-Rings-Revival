# Local clips (not in git)

Put clips here that you may not redistribute, such as Unity Asset Store animations. Git ignores everything in
this folder except this file and `.gitignore`.

    blender -b -P tools/anim-viewer/fbx_to_bvh.py -- ~/Downloads/SomePack --race orc

`orc.<name>.bvh` files show under the orc race in the viewer's clip library (**Load local**); files without a
race prefix show for every race. See tools/anim-viewer/README.md.
