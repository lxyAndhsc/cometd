/*
 * Copyright (c) 2008-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

(function(root, factory){
    if (typeof exports === 'object') {
        module.exports = factory(require('cometd'));
    } else if (typeof define === 'function' && define.amd) {
        define(['org/cometd'], factory);
    } else {
        factory(root.org.cometd);
    }
}(this, function(cometdModule) {
    // The timestamp extension adds the optional timestamp field to all outgoing messages.
    return cometdModule.TimeStampExtension = function() {
        this.outgoing = function(message) {
            message.timestamp = new Date().toUTCString();
            return message;
        };
    };
}));
