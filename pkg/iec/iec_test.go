/*
 * Copyright 2020 ForgeRock AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package iec

import (
	"fmt"
	"github.com/ForgeRock/iot-edge/internal/mock"
	"github.com/ForgeRock/iot-edge/internal/tokencache"
	"github.com/ForgeRock/iot-edge/pkg/message"
	"testing"
	"time"
)

// check that the Auth Id Key is not sent to AM
func TestIEC_Authenticate_AuthIdKey_Is_Not_Sent(t *testing.T) {
	authId := "12345"
	mockClient := &mock.Client{
		AuthenticateFunc: func(_ string, payload message.AuthenticatePayload) (reply message.AuthenticatePayload, err error) {
			if payload.AuthIDKey != "" {
				return reply, fmt.Errorf("don't send auth id digest")
			}
			reply.AuthId = authId
			return reply, nil

		}}
	controller := IEC{
		Client:    mockClient,
		authCache: tokencache.New(5*time.Minute, 10*time.Minute),
	}
	reply, err := controller.Authenticate("tree", message.AuthenticatePayload{})
	if err != nil {
		t.Fatal(err)
	}
	_, err = controller.Authenticate("tree", reply)
	if err != nil {
		t.Fatal(err)
	}
}

// check that the Auth Id is not returned by the IEC to the Thing
func TestIEC_Authenticate_AuthId_Is_Not_Returned(t *testing.T) {
	authId := "12345"
	mockClient := &mock.Client{
		AuthenticateFunc: func(_ string, _ message.AuthenticatePayload) (reply message.AuthenticatePayload, _ error) {
			reply.AuthId = authId
			return reply, nil

		}}
	controller := IEC{
		Client:    mockClient,
		authCache: tokencache.New(5*time.Minute, 10*time.Minute),
	}
	reply, _ := controller.Authenticate("tree", message.AuthenticatePayload{})
	if reply.AuthId != "" {
		t.Fatal("AuthId has been returned")
	}
}

// check that the Auth Id is cached by the IEC
func TestIEC_Authenticate_AuthId_Is_Cached(t *testing.T) {
	authId := "12345"
	mockClient := &mock.Client{
		AuthenticateFunc: func(_ string, _ message.AuthenticatePayload) (reply message.AuthenticatePayload, _ error) {
			reply.AuthId = authId
			return reply, nil

		}}
	controller := IEC{
		Client:    mockClient,
		authCache: tokencache.New(5*time.Minute, 10*time.Minute),
	}
	reply, _ := controller.Authenticate("tree", message.AuthenticatePayload{})
	id, ok := controller.authCache.Get(reply.AuthIDKey)
	if !ok {
		t.Fatal("The authId has not been stored")
	}
	if id != authId {
		t.Error("The stored authId is not correct")
	}
}