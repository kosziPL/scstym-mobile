# Checkpoint before ArchiveTune 15

The pre-upgrade application is commit `43feecab1`. Its `core` submodule
points to `ec51bc263d7e777ca76ea37b7213349caf2e184e`.
`core-before-15.patch` preserves the uncommitted core changes present at
the time of the checkpoint. Generated build directories are excluded.

To restore this checkpoint in a fresh checkout of the checkpoint commit:

```sh
git submodule update --init --recursive
git -C core apply ../patches/core-before-15.patch
```
