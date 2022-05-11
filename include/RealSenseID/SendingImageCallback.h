// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2020-2021 Intel Corporation. All Rights Reserved.

#pragma once

namespace RealSenseID
{

class SendingImageCallback
{
public:
    virtual ~SendingImageCallback(void) = default;

    virtual bool AbortSendingImage(int idx, int total)
    {
        return false;
    }
};

} // namespace RealSenseID
