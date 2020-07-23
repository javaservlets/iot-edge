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

package session

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/x509"
	"github.com/ForgeRock/iot-edge/pkg/callback"
	"math/big"
	"strings"
	"testing"
)

func Test_processCallbacks(t *testing.T) {
	var signingKey, _ = ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	template := x509.Certificate{
		SerialNumber: big.NewInt(1),
	}
	certBytes, _ := x509.CreateCertificate(rand.Reader, &template, &template, signingKey.Public(), signingKey)
	cert, _ := x509.ParseCertificate(certBytes)

	authCB := callback.Callback{
		Type:   callback.TypeHiddenValueCallback,
		Output: []callback.Entry{{Name: "id", Value: "jwt-pop-authentication"}, {Name: "value", Value: "1"}},
		Input:  make([]callback.Entry, 1),
	}
	regCB := callback.Callback{
		Type:   callback.TypeHiddenValueCallback,
		Output: []callback.Entry{{Name: "id", Value: "jwt-pop-registration"}, {Name: "value", Value: "1"}},
		Input:  make([]callback.Entry, 1),
	}
	nameCB := callback.Callback{
		Type:   callback.TypeNameCallback,
		Output: make([]callback.Entry, 1),
		Input:  make([]callback.Entry, 1),
	}
	callbacks := []callback.Callback{nameCB, authCB, regCB}
	authHL := callback.AuthenticateHandler{
		Realm:   "/",
		ThingID: "Bob",
		KeyID:   "1",
		Key:     signingKey,
	}
	regHL := callback.RegisterHandler{
		Realm:        "/",
		ThingID:      "Bob",
		ThingType:    callback.TypeDevice,
		KeyID:        "1",
		Key:          signingKey,
		Certificates: []*x509.Certificate{cert},
	}
	nameHL := callback.NameHandler{Name: "Bob"}

	tests := []struct {
		name     string
		handlers []callback.Handler
		pop      bool
	}{
		{name: "Name/Auth/Reg", handlers: []callback.Handler{nameHL, authHL, regHL}, pop: true},
		{name: "Name/Auth", handlers: []callback.Handler{nameHL, authHL}, pop: true},
		{name: "Name/Reg", handlers: []callback.Handler{nameHL, regHL}, pop: true},
		{name: "Auth/Reg", handlers: []callback.Handler{authHL, regHL}, pop: true},
		{name: "Name", handlers: []callback.Handler{nameHL}, pop: false},
		{name: "Auth", handlers: []callback.Handler{authHL}, pop: true},
		{name: "Reg", handlers: []callback.Handler{regHL}, pop: true},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			isPop, err := processCallbacks(test.handlers, callbacks)
			if err != nil {
				t.Errorf("processCallbacks() - unexpected error: %s", err)
			}
			if test.pop != isPop {
				t.Errorf("processCallbacks() PoP restricted = %v, wanted %v", isPop, test.pop)
			}
		})
	}
}

func TestDefaultSession_RequestBody(t *testing.T) {
	sesh := &DefaultSession{
		key:           nil,
		popRestricted: true,
	}
	_, _, err := sesh.RequestBody("", "", nil)
	if err == nil || !strings.Contains(err.Error(), "no signing key was configured") {
		t.Error("Expected RequestBody() to fail: err = ", err)
	}
}