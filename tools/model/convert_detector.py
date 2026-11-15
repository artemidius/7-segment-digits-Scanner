"""Convert a YOLO character detector to LiteRT, preserving ONNX pixel coordinates."""
import argparse
import ast
import contextlib
import hashlib
import json
from pathlib import Path
import shutil
import tempfile
import numpy as np


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('weights',type=Path,help='Local .pt weights or a YOLO .onnx export.')
    parser.add_argument('--output',type=Path,required=True)
    args = parser.parse_args()
    args.output = args.output.resolve()
    args.weights = args.weights.resolve()
    args.output.mkdir(parents=True,exist_ok=True)
    source = args.weights
    if source.suffix == '.pt':
        from ultralytics import YOLO
        source = Path(YOLO(str(source)).export(format='onnx',imgsz=640,simplify=False,opset=17,device='cpu'))
    import onnxruntime as ort
    session = ort.InferenceSession(str(source),providers=['CPUExecutionProvider'])
    names = ast.literal_eval(session.get_modelmeta().custom_metadata_map['names'])
    # Constant-fold the decode head before layout conversion. Without this,
    # onnx2tf can transpose the stride multiplication incorrectly.
    import onnx
    from onnxslim import slim
    simplified = args.output/'simplified.onnx'
    onnx.save(slim(str(source)),simplified)
    from onnx2tf import convert
    # onnx2tf downloads a layout-check sample even for float exports. Supply a
    # deterministic local sample instead; real-photo parity is checked separately.
    with tempfile.TemporaryDirectory(dir=args.output) as scratch, contextlib.chdir(scratch):
        np.save('calibration_image_sample_data_20x128x128x3_float32.npy',
                np.random.default_rng(42).random((20,128,128,3),dtype=np.float32))
        convert(input_onnx_file_path=str(simplified),output_folder_path=str(args.output/'saved_model'),
                not_use_onnxsim=True,verbosity='error',output_signaturedefs=True)
    candidates = list((args.output/'saved_model').glob('*_float16.tflite'))
    if len(candidates)!=1:
        raise RuntimeError(f'Expected one float16 model; got {candidates}')
    target = args.output/'detector.tflite'
    shutil.copyfile(candidates[0],target)
    metadata = {'names':names, 'box_units':'input_pixels', 'input':'RGB NHWC float32 / 255',
                'output':'[1, 4 + number_of_classes, candidates]; xywh + class scores',
                'source_sha256':hashlib.sha256(args.weights.read_bytes()).hexdigest(),
                'model_sha256':hashlib.sha256(target.read_bytes()).hexdigest()}
    target.with_suffix('.json').write_text(json.dumps(metadata,indent=2)+'\n')
    print(f'Created {target} ({target.stat().st_size} bytes). License follows the source weights.')


if __name__=='__main__':
    main()
