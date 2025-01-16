# ImgLib2 Appose helpers

[Appose](https://github.com/apposed/appose) is a library for interprocess cooperation with shared memory.

This project houses utility code for working with ImgLib2 images backed by named shared memory buffers.
Such images can be passed to worker processes such as Python scripts using Appose, so that computation
can be performed in both Java and Python on the same image data without copying pixels.

## Usage

`net.imglib2.appose.NDArrays` contains static methods for working with Appose `NDArray`, most importantly:
```java
NDArray ndArray = NDArrays.asNDArray(img);
```
creates an `NDArray` with shape and data type corresponding to the shape and
ImgLib2 type (must be `NativeType`) of the image.
This can be put into Appose Task `inputs`.
See [these examples](https://github.com/imglib/imglib2-appose/blob/-/src/test/java/net/imglib2/appose/ShmImgTest.java).

`net.imglib2.appose.ShmImg<T>` is an `Img<T>` implementation that wraps an `ArrayImg` that wraps an `NDArray`.
If a `ShmImg` is passed to `NDArrays.asNDArray(img)` then the wrapped `NDArray` is returned directly. So, no copying.

Create a `ShmImg<T>` with
```java
Img<FloatType> img = new ShmImg<>(new FloatType(), 4, 3, 2);
```

Wrap it around an existing `NDArray` using 
```java
NDArray ndArray = ...;
Img<FloatType> img = new ShmImg<>(ndArray);
```
(The `ShmImg` will have pixel type corresponding to the
`ndArray.dType()`. Here we assume that the dType is `FLOAT32`,
so we assign it to `Img<FloatType>`.)

## Example

```java
// Create an Img backed by memory that can be shared with Python.
Img<FloatType> img = new ShmImg<>(new FloatType(), dims);
populateImgValues(img);

// Or if you already have an Img that isn't backed
// by shared memory, you can copy it to one that is:
Img<FloatType> img = ShmImg.copyOf(myExistingImg);

// Define a Python script that operates on a numpy array.
// This assumes the input will be an appose.NDArray called `image`.
String script = """
# Wrap the input image from appose.NDArray to numpy.ndarray.
narr = image.ndarray()

# Do some calculation with it.
import skimage
blurred = skimage.filters.gaussian(narr, sigma=5)

# Create an output NDArray backed by shared memory.
import appose
shared = appose.NDArray(blurred.dtype, blurred.shape)

# Copy the blurred image into it.
shared.ndarray()[:] = narr

# Pass the output NDArray back via the task's outputs.
task.outputs['blurred'] = shared

# Alternately, instead of allocating another shared image
# and passing it back, you could overwrite the input image
by copying the blurred result onto it:
#narr[:] = blurred
# And then simply access the original img from Java. 8)
# But this trick only works if the computation result is
# the same shape and dtype as the input.
""";

// Construct the Appose environment and Python worker process.
// The environment.yml should depend on appose, numpy, and scikit-image.
// After the environment is built once, it will be reused.
Environment env = Appose.file("environment.yml").logDebug().build();
try (Service python : env.python()) {
    // Store our Img into a map of inputs to the Python script.
    final Map<String, Object> inputs = new HashMap<>();
    inputs.put("image", NDArrays.asNDArray(img));

    // Run the script!
    Task task = python.task(script, inputs);
    task.waitFor();

    // Verify that it worked.
    if (task.status != TaskStatus.COMPLETE) {
        throw new RuntimeException("Python script failed with error: " + task.error);
    }

    // Access the `blurred` output NDArray.
    NDArray blurred = (NDArray) task.outputs.get("blurred");
    // Wrap the NDArray to an Img.
    Img<FloatType> blurredImg = new ShmImg<>(blurred);

    // Now do whatever you want with `blurredImg` as usual.
    // ...
}
```
