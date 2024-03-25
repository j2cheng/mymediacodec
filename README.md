# MediaCodec prototype

> Version 20210830

This project demonstrate how to use MediaCodec to do H/W decoding H264 bit stream, help verify platform behavior quickly.

## Decoder

Main decoder interface, read input from DecoderInput, write output to DecoderOutput.

## NalParser

Parse raw H264 file, searching for NAL CSD, report NALU one by one with size and type.

Check NalParserTest for usage.

## NalPolicy

A policy callback for NalParser to split the Nals, support action CUT, CONTINUE, SKIP, check NalPolicyTest for usage. 

## Basic test

Launch app and add multiple types of sessions, and than click the start/stop button to decode and render embed H264 stream.

Different product flavor provide different sample videos, flavor 'benchmarkDebug' will provide a standard 1920x1080@60 video, add multiple sessions can reach the device's capability easily. 

## Advanced test

AndroidTest DecoderMediaCodecTest provide multiple test cases to verify advanced combination, feed with zero pts, uptime pts or media time pts, drop frame or not. It is very easy to assemble different strategy for input and output to verify MediaCodec's working behavior.

It will auto launch a temp activity with a SurfaceView embed, auto start a MediaCodec to decode and render the video, and auto quit the activity after decode finish.

