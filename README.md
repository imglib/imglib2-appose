# ImgLib2 Appose helpers

***WARNING: Appose is currently in incubation.***

[Appose](https://github.com/apposed/appose) is a library for interprocess cooperation with shared memory.
The guiding principles are *simplicity* and *efficiency*.

This repository relies on the following PRs:
* https://github.com/apposed/appose-java/pull/5
* https://github.com/apposed/appose-python/pull/1

## Usage

`net.imglib2.appose.NDArrayUtils` contains static methods for working with Appose `NDArray`, most importantly:
```java
NDArray ndArray = NDArrayUtils.asNDArray(img);
```
creates a `NDArray` with shape and data type corresponding to the shape and
ImgLib2 type (must be `NativeType`) of the image.
This can be put into Appose Task `inputs`.
See [these examples](https://github.com/imglib/imglib2-appose/blob/6ae502b919588b880fe1b30700b914d3733407a3/src/test/java/net/imglib2/appose/SharedMemoryImgExamples.java).

`net.imglib2.appose.SharedMemoryImg<T>` is a `Img<T>` implementation that wraps an `ArrayImg` that wraps a `NDArray`.
If a `SharedMemoryImg` is passed to `NDArrayUtils.asNDArray(img)` then the wrapped `NDArray` is returned directly. So, no copying.

Create a `SharedMemoryImg<T>` with
```java
Img<FloatType> img = new SharedMemoryImg<>(new FloatType(), 4, 3, 2);
```

Wrap it around an existing `NDArray` using 
```java
NDArray ndArray;
Img<FloatType> img = new SharedMemoryImg<>(ndArray);
```
(The `SharedMemoryImg` will have pixel type corresponding to the
`ndArray.dType()`. Here we assume that the dType is `FLOAT32`, so we assign it
to `Img<FloatType>`.)
