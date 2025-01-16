# ImgLib2 Appose helpers

[Appose](https://github.com/apposed/appose) is a library for interprocess cooperation with shared memory.
The guiding principles are *simplicity* and *efficiency*.

This repository relies on the following PRs:
* https://github.com/apposed/appose-java/pull/5
* https://github.com/apposed/appose-python/pull/1

## Usage

`net.imglib2.appose.NDArrays` contains static methods for working with Appose `NDArray`, most importantly:
```java
NDArray ndArray = NDArrays.asNDArray(img);
```
creates a `NDArray` with shape and data type corresponding to the shape and
ImgLib2 type (must be `NativeType`) of the image.
This can be put into Appose Task `inputs`.
See [these examples](https://github.com/imglib/imglib2-appose/blob/-/src/test/java/net/imglib2/appose/ShmImgTest.java).

`net.imglib2.appose.ShmImg<T>` is a `Img<T>` implementation that wraps an `ArrayImg` that wraps a `NDArray`.
If a `ShmImg` is passed to `NDArrays.asNDArray(img)` then the wrapped `NDArray` is returned directly. So, no copying.

Create a `ShmImg<T>` with
```java
Img<FloatType> img = new ShmImg<>(new FloatType(), 4, 3, 2);
```

Wrap it around an existing `NDArray` using 
```java
NDArray ndArray;
Img<FloatType> img = new ShmImg<>(ndArray);
```
(The `ShmImg` will have pixel type corresponding to the
`ndArray.dType()`. Here we assume that the dType is `FLOAT32`, so we assign it
to `Img<FloatType>`.)
